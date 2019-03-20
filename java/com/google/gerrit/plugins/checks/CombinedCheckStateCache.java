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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.CacheStats;
import com.google.common.cache.LoadingCache;
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

/**
 * Cache of {@link CombinedCheckState} per change.
 *
 * <p>In the absence of plugin-defined index fields, this cache is used to performantly populate the
 * {@code combinedState} field in {@code ChangeCheckInfo} in the query path.
 */
@Singleton
public class CombinedCheckStateCache {
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

  private final LoadingCache<CombinedCheckStateCacheKeyProto, CombinedCheckState> cache;
  private final Loader loader;

  @Inject
  CombinedCheckStateCache(
      @Named(NAME) LoadingCache<CombinedCheckStateCacheKeyProto, CombinedCheckState> cache,
      Loader loader) {
    this.cache = cache;
    this.loader = loader;
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
    CombinedCheckStateCacheKeyProto key = key(project, psId);
    CombinedCheckState newState = loader.load(key);
    CombinedCheckState oldState = cache.getIfPresent(key);
    if (newState != oldState) {
      cache.put(key, newState);
    }
    return newState;
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
