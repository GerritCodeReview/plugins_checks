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

package com.google.gerrit.plugins.checks.api;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.plugins.checks.AdministrateCheckersPermission;
import com.google.gerrit.plugins.checks.Check;
import com.google.gerrit.plugins.checks.Checker;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.Checkers;
import com.google.gerrit.plugins.checks.Checks;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.query.change.ChangeStatusPredicate;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.query.change.ProjectPredicate;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryHelper.ActionType;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.kohsuke.args4j.Option;

public class ListPendingChecks implements RestReadView<TopLevelResource> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final PermissionBackend permissionBackend;
  private final AdministrateCheckersPermission permission;
  private final Checkers checkers;
  private final Checks checks;
  private final RetryHelper retryHelper;
  private final Provider<ChangeQueryBuilder> queryBuilderProvider;
  private final Provider<InternalChangeQuery> changeQueryProvider;

  private CheckerUuid checkerUuid;
  private List<CheckState> states = new ArrayList<>(CheckState.values().length);

  @Option(
      name = "--checker",
      metaVar = "UUID",
      usage = "checker UUID formatted as '<scheme>:<id>'",
      handler = CheckerUuidHandler.class)
  public void setChecker(CheckerUuid checkerUuid) {
    this.checkerUuid = checkerUuid;
  }

  @Option(name = "--state", metaVar = "STATE", usage = "check state")
  public void addState(CheckState state) {
    this.states.add(state);
  }

  @Inject
  public ListPendingChecks(
      PermissionBackend permissionBackend,
      AdministrateCheckersPermission permission,
      Checkers checkers,
      Checks checks,
      RetryHelper retryHelper,
      Provider<ChangeQueryBuilder> queryBuilderProvider,
      Provider<InternalChangeQuery> changeQueryProvider) {
    this.permissionBackend = permissionBackend;
    this.permission = permission;
    this.checkers = checkers;
    this.checks = checks;
    this.retryHelper = retryHelper;
    this.queryBuilderProvider = queryBuilderProvider;
    this.changeQueryProvider = changeQueryProvider;
  }

  @Override
  public List<PendingChecksInfo> apply(TopLevelResource resource)
      throws RestApiException, PermissionBackendException, IOException, ConfigInvalidException,
          OrmException {
    permissionBackend.currentUser().check(permission);

    if (states.isEmpty()) {
      // If no state was specified, assume NOT_STARTED by default.
      states.add(CheckState.NOT_STARTED);
    }

    if (checkerUuid == null) {
      throw new BadRequestException("checker UUID is required");
    }

    Checker checker =
        checkers
            .getChecker(checkerUuid)
            .orElseThrow(
                () ->
                    new UnprocessableEntityException(
                        String.format("checker %s not found", checkerUuid)));

    if (checker.getStatus() == CheckerStatus.DISABLED) {
      return ImmutableList.of();
    }

    // The query system can only match against the current patch set; ignore non-current patch sets
    // for now.
    List<ChangeData> changes = queryMatchingChangesFor(checker);
    List<PendingChecksInfo> pendingChecks = new ArrayList<>(changes.size());
    for (ChangeData cd : changes) {
      getMatchingPendingChecks(cd.project(), cd.currentPatchSet().getId())
          .ifPresent(pendingChecks::add);
    }
    return pendingChecks;
  }

  private List<ChangeData> queryMatchingChangesFor(Checker checker)
      throws ConfigInvalidException, OrmException {
    Predicate<ChangeData> predicate = new ProjectPredicate(checker.getRepository().get());
    if (checker.getQuery().isPresent()) {
      String query = checker.getQuery().get();
      try {
        predicate = Predicate.and(predicate, queryBuilderProvider.get().parse(query));
      } catch (QueryParseException e) {
        logger.atWarning().withCause(e).log(
            "invalid query for checker %s: %s", checker.getUuid(), query);
        throw new ConfigInvalidException(
            String.format("change query of checker %s is invalid", checkerUuid), e);
      }
    }

    if (!CheckBackfiller.hasStatusPredicate(predicate)) {
      predicate = Predicate.and(ChangeStatusPredicate.open(), predicate);
    }

    return executeIndexQueryWithRetry(predicate);
  }

  private List<ChangeData> executeIndexQueryWithRetry(Predicate<ChangeData> predicate)
      throws OrmException {
    try {
      return retryHelper.execute(
          ActionType.INDEX_QUERY,
          () -> changeQueryProvider.get().query(predicate),
          OrmException.class::isInstance);
    } catch (Exception e) {
      Throwables.throwIfUnchecked(e);
      Throwables.throwIfInstanceOf(e, OrmException.class);
      throw new OrmException(e);
    }
  }

  private Optional<PendingChecksInfo> getMatchingPendingChecks(
      Project.NameKey project, PatchSet.Id patchSetId) throws OrmException, IOException {
    CheckState checkState = getCheckState(project, patchSetId);
    if (!states.contains(checkState)) {
      return Optional.empty();
    }
    return Optional.of(createPendingChecksInfo(project, patchSetId, checkerUuid, checkState));
  }

  private CheckState getCheckState(Project.NameKey project, PatchSet.Id patchSetId)
      throws OrmException, IOException {
    Optional<Check> check =
        checks
            .getChecks(project, patchSetId)
            .stream()
            .filter(c -> c.key().checkerUuid().equals(checkerUuid))
            .findFirst();

    // Backfill if check is not present.
    return check.map(Check::state).orElse(CheckState.NOT_STARTED);
  }

  private static PendingChecksInfo createPendingChecksInfo(
      Project.NameKey project,
      PatchSet.Id patchSetId,
      CheckerUuid checkerUuid,
      CheckState checkState) {
    PendingChecksInfo pendingChecksInfo = new PendingChecksInfo();

    pendingChecksInfo.patchSet = new CheckablePatchSetInfo();
    pendingChecksInfo.patchSet.project = project.get();
    pendingChecksInfo.patchSet.changeNumber = patchSetId.getParentKey().get();
    pendingChecksInfo.patchSet.patchSetId = patchSetId.get();

    pendingChecksInfo.pendingChecks =
        ImmutableMap.of(checkerUuid.toString(), new PendingCheckInfo(checkState));

    return pendingChecksInfo;
  }
}
