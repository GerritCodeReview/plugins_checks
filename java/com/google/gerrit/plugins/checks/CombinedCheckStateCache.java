// Copyright (C) 2019 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.plugins.checks;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.CacheStats;
import com.google.common.cache.LoadingCache;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.AtomicLongMap;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Description.Units;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.Timer1;
import com.google.gerrit.plugins.checks.api.CombinedCheckState;
import com.google.gerrit.plugins.checks.cache.proto.Cache.CombinedCheckStateCacheKeyProto;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.cache.serialize.EnumCacheSerializer;
import com.google.gerrit.server.cache.serialize.ProtobufSerializer;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Cache of {@link CombinedCheckState} per change.
 *
 * <p>In the absence of plugin-defined index fields, this cache is used to performantly populate the
 * {@code combinedState} field in {@code ChangeCheckInfo} in the query path.
 */
@Singleton
public class CombinedCheckStateCache {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static final String NAME = "combined_check_state";

  public static Module module() {
    return new CacheModule() {
      @Override
      public void configure() {
        persist(NAME, CombinedCheckStateCacheKeyProto.class, CombinedCheckState.class)
            .version(1)
            .maximumWeight(10000)
            .diskLimit(-1)
            .keySerializer(new ProtobufSerializer<>(CombinedCheckStateCacheKeyProto.parser()))
            .valueSerializer(new EnumCacheSerializer<>(CombinedCheckState.class))
            .loader(Loader.class);
      }
    };
  }

  @Singleton
  static class Metrics {
    // Pair of metric and manual counters, to work around the fact that metric classes have no
    // getters.
    private final Timer1<Boolean> reloadLatency;
    private final AtomicLongMap<Boolean> reloadCount;

    @Inject
    Metrics(MetricMaker metricMaker) {
      reloadLatency =
          metricMaker.newTimer(
              "checks/reload_combined_check_state",
              new Description("Latency for reloading combined check state")
                  .setCumulative()
                  .setUnit(Units.MILLISECONDS),
              Field.ofBoolean("dirty", "whether reloading resulted in updating the cached value"));
      reloadCount = AtomicLongMap.create();
    }

    void recordReload(boolean dirty, long elapsed, TimeUnit timeUnit) {
      reloadLatency.record(dirty, elapsed, timeUnit);
      reloadCount.incrementAndGet(dirty);
    }

    long getReloadCount(boolean dirty) {
      return reloadCount.get(dirty);
    }
  }

  private final LoadingCache<CombinedCheckStateCacheKeyProto, CombinedCheckState> cache;
  private final Loader loader;
  private final Metrics metrics;

  @Inject
  CombinedCheckStateCache(
      @Named(NAME) LoadingCache<CombinedCheckStateCacheKeyProto, CombinedCheckState> cache,
      Loader loader,
      Metrics metrics) {
    this.cache = cache;
    this.loader = loader;
    this.metrics = metrics;
  }

  /**
   * Get the state from the cache, computing it from checks ref if necessary.
   *
   * @param project project containing the change.
   * @param psId patch set to which the state corresponds.
   * @return combined check state.
   * @throws OrmException if an error occurred.
   */
  public CombinedCheckState get(Project.NameKey project, PatchSet.Id psId) throws OrmException {
    try {
      return cache.get(key(project, psId));
    } catch (ExecutionException e) {
      throw new OrmException(e);
    }
  }

  /**
   * Load the state from primary storage, and update the state in the cache only if it changed.
   *
   * <p>This method does a cache lookup followed by a write, which is inherently racy. It is
   * intended to be used whenever we need to recompute the combined check state, for example when
   * sending a {@code ChangeCheckInfo} to the client. As a result, inconsistencies between the cache
   * and the actual state should tend to get fixed up immediately after a user views the change.
   *
   * @param project project containing the change.
   * @param psId patch set to which the state corresponds.
   * @return combined check state.
   */
  public CombinedCheckState reload(Project.NameKey project, PatchSet.Id psId) throws OrmException {
    // Possible future optimizations, depending on real-world latency:
    //  * Make this whole method async, in the FanOutExecutor.
    //  * Short-circuit before calling this method, if an individual check transitioned between two
    //    CheckStates which would result in the same CombinedCheckState.
    Stopwatch sw = Stopwatch.createStarted();
    Boolean dirty = null;
    try {
      CombinedCheckStateCacheKeyProto key = key(project, psId);
      CombinedCheckState newState = loader.load(key);
      CombinedCheckState oldState = cache.getIfPresent(key);
      if (newState != oldState) {
        dirty = true;
        cache.put(key, newState);
      } else {
        dirty = false;
      }
      return newState;
    } finally {
      if (dirty == null) {
        // Exception while loading value, so we don't know if it's dirty. Record a metric anyway,
        // arbitrarily assuming dirty.
        dirty = true;
      }
      metrics.recordReload(dirty, sw.elapsed(NANOSECONDS), NANOSECONDS);
    }
  }

  /**
   * Update the state in the cache only if it changed.
   *
   * <p>This method does a cache lookup followed by a write, which is inherently racy.
   * Inconsistencies between the cache and the actual state should tend to get fixed up immediately
   * after a user views the change, since the read path calls {@link #reload(Project.NameKey,
   * PatchSet.Id)}.
   *
   * @param project project containing the change.
   * @param psId patch set to which the state corresponds.
   */
  public void updateIfNecessary(Project.NameKey project, PatchSet.Id psId) {
    try {
      reload(project, psId);
    } catch (OrmException e) {
      logger.atWarning().withCause(e).log(
          "failed to reload CombinedCheckState for %s in %s", psId, project);
    }
  }

  /**
   * Directly put a state into the cache.
   *
   * @param project project containing the change.
   * @param psId patch set to which the state corresponds.
   * @param state combined check state.
   */
  @VisibleForTesting
  public void putForTest(Project.NameKey project, PatchSet.Id psId, CombinedCheckState state) {
    cache.put(key(project, psId), state);
  }

  @VisibleForTesting
  public long getReloadCount(boolean dirty) {
    return metrics.getReloadCount(dirty);
  }

  @VisibleForTesting
  public CacheStats getStats() {
    return cache.stats();
  }

  private static CombinedCheckStateCacheKeyProto key(Project.NameKey project, PatchSet.Id psId) {
    return CombinedCheckStateCacheKeyProto.newBuilder()
        .setProject(project.get())
        .setChangeId(psId.getParentKey().get())
        .setPatchSetId(psId.get())
        .build();
  }

  @Singleton
  private static class Loader
      extends CacheLoader<CombinedCheckStateCacheKeyProto, CombinedCheckState> {
    private final Checks checks;

    @Inject
    Loader(Checks checks) {
      this.checks = checks;
    }

    @Override
    public CombinedCheckState load(CombinedCheckStateCacheKeyProto key) throws OrmException {
      try {
        return checks.getCombinedCheckState(
            new Project.NameKey(key.getProject()),
            new PatchSet.Id(new Change.Id(key.getChangeId()), key.getPatchSetId()));
      } catch (IOException e) {
        throw new OrmException(e);
      }
    }
  }
}
