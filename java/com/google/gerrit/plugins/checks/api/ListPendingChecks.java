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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.index.query.AndPredicate;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.plugins.checks.Check;
import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.Checker;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.Checkers;
import com.google.gerrit.plugins.checks.Checks;
import com.google.gerrit.plugins.checks.Checks.GetCheckOptions;
import com.google.gerrit.plugins.checks.index.CheckQueryBuilder;
import com.google.gerrit.plugins.checks.index.CheckStatePredicate;
import com.google.gerrit.plugins.checks.index.CheckerPredicate;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.query.change.ChangeQueryProcessor;
import com.google.gerrit.server.update.RetryHelper;
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
  private final CheckQueryBuilder checkQueryBuilder;
  private final Checkers checkers;
  private final Checks checks;
  private final RetryHelper retryHelper;
  private final Provider<ChangeQueryBuilder> queryBuilderProvider;
  private final Provider<ChangeQueryProcessor> changeQueryProcessorProvider;

  private String queryString;

  @Option(
      name = "--query",
      aliases = {"-q"},
      metaVar = "QUERY",
      usage = "check query")
  public void setQuery(String queryString) {
    this.queryString = queryString;
  }

  @Inject
  public ListPendingChecks(
      CheckQueryBuilder checkQueryBuilder,
      Checkers checkers,
      Checks checks,
      RetryHelper retryHelper,
      Provider<ChangeQueryBuilder> queryBuilderProvider,
      Provider<ChangeQueryProcessor> changeQueryProcessorProvider) {
    this.checkQueryBuilder = checkQueryBuilder;
    this.checkers = checkers;
    this.checks = checks;
    this.retryHelper = retryHelper;
    this.queryBuilderProvider = queryBuilderProvider;
    this.changeQueryProcessorProvider = changeQueryProcessorProvider;
  }

  @Override
  public List<PendingChecksInfo> apply(TopLevelResource resource)
      throws RestApiException, IOException, ConfigInvalidException, OrmException {
    if (queryString == null) {
      throw new BadRequestException("query is required");
    }

    Predicate<Check> query = validateQuery(parseQuery(queryString));
    if (!hasStatePredicate(query)) {
      query = Predicate.and(new CheckStatePredicate(CheckState.NOT_STARTED), query);
    }

    Optional<Checker> checker = checkers.getChecker(getCheckerUuidFromQuery(query));
    if (!checker.isPresent() || checker.get().isDisabled()) {
      return ImmutableList.of();
    }

    // The query system can only match against the current patch set; ignore non-current patch sets
    // for now.
    List<ChangeData> changes =
        checker
            .get()
            .queryMatchingChanges(
                retryHelper, queryBuilderProvider.get(), changeQueryProcessorProvider);
    CheckerUuid checkerUuid = checker.get().getUuid();
    List<PendingChecksInfo> pendingChecks = new ArrayList<>(changes.size());
    for (ChangeData cd : changes) {
      PatchSet patchSet = cd.currentPatchSet();
      CheckKey checkKey = CheckKey.create(cd.project(), patchSet.getId(), checkerUuid);

      // Backfill if check is not present.
      // Backfilling is only done for relevant checkers (checkers where the repository and the query
      // matches the change). Since the change was found by executing the query of the checker we
      // know
      // that the checker is relevant for this patch set and hence backfilling should be done.
      Check check =
          checks
              .getCheck(checkKey, GetCheckOptions.defaults())
              .orElseGet(() -> Check.newBackfilledCheck(cd, patchSet, checker.get()));

      if (query.asMatchable().match(check)) {
        pendingChecks.add(createPendingChecksInfo(cd.project(), patchSet, checkerUuid, check));
      }
    }
    return pendingChecks;
  }

  private Predicate<Check> parseQuery(String query) throws BadRequestException {
    try {
      return checkQueryBuilder.parse(query.trim());
    } catch (QueryParseException e) {
      throw new BadRequestException(e.getMessage());
    }
  }

  private static Predicate<Check> validateQuery(Predicate<Check> predicate)
      throws BadRequestException {
    if (countCheckerPredicates(predicate) != 1)
      throw new BadRequestException(
          String.format(
              "query must contain exactly 1 '%s' operator", CheckQueryBuilder.FIELD_CHECKER));

    if (predicate instanceof AndPredicate) {
      if (!predicate.getChildren().stream().anyMatch(CheckerPredicate.class::isInstance)) {
        throw new BadRequestException(
            String.format(
                "query must be '%s=<checker-uuid>' or '%s=<checker-uuid> AND <other-operators>'",
                CheckQueryBuilder.FIELD_CHECKER, CheckQueryBuilder.FIELD_CHECKER));
      }
    } else if (!(predicate instanceof CheckerPredicate)) {
      throw new BadRequestException(
          String.format(
              "query must be '%s=<checker-uuid>' or '%s=<checker-uuid> AND <other-operators>'",
              CheckQueryBuilder.FIELD_CHECKER, CheckQueryBuilder.FIELD_CHECKER));
    }
    return predicate;
  }

  private static boolean hasStatePredicate(Predicate<Check> predicate) {
    if (predicate instanceof CheckStatePredicate) {
      return true;
    }
    if (predicate.getChildCount() == 0) {
      return false;
    }
    return predicate.getChildren().stream().anyMatch(ListPendingChecks::hasStatePredicate);
  }

  private static int countCheckerPredicates(Predicate<Check> predicate) {
    if (predicate instanceof CheckerPredicate) {
      return 1;
    }
    if (predicate.getChildCount() == 0) {
      return 0;
    }
    return predicate.getChildren().stream()
        .mapToInt(ListPendingChecks::countCheckerPredicates)
        .sum();
  }

  private static CheckerUuid getCheckerUuidFromQuery(Predicate<Check> predicate) {
    // the query validation (see #validateQuery(Predicate<Check>)) ensures that there is exactly 1
    // CheckerPredicate and that it is on the first or second level of the predicate tree.

    if (predicate instanceof CheckerPredicate) {
      return ((CheckerPredicate) predicate).getCheckerUuid();
    }

    checkState(predicate.getChildCount() > 0, "no checker predicate found: %s", predicate);
    Optional<CheckerPredicate> checkerPredicate =
        predicate.getChildren().stream()
            .filter(CheckerPredicate.class::isInstance)
            .map(p -> (CheckerPredicate) p)
            .findAny();
    return checkerPredicate
        .map(CheckerPredicate::getCheckerUuid)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    String.format("no checker predicate found: %s", predicate)));
  }

  private static PendingChecksInfo createPendingChecksInfo(
      Project.NameKey project, PatchSet patchSet, CheckerUuid checkerUuid, Check check) {
    PendingChecksInfo pendingChecksInfo = new PendingChecksInfo();

    pendingChecksInfo.patchSet = new CheckablePatchSetInfo();
    pendingChecksInfo.patchSet.project = project.get();
    pendingChecksInfo.patchSet.changeNumber = patchSet.getId().getParentKey().get();
    pendingChecksInfo.patchSet.patchSetId = patchSet.getPatchSetId();

    pendingChecksInfo.pendingChecks =
        ImmutableMap.of(checkerUuid.toString(), new PendingCheckInfo(check.state()));

    return pendingChecksInfo;
  }
}
