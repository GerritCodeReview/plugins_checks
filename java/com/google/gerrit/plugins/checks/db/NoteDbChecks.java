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

package com.google.gerrit.plugins.checks.db;

import static java.util.stream.Collectors.toMap;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.index.query.IndexPredicate;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.plugins.checks.Check;
import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.Checker;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.Checkers;
import com.google.gerrit.plugins.checks.Checks;
import com.google.gerrit.plugins.checks.api.CheckState;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.query.change.ChangeStatusPredicate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/** Class to read checks from NoteDb. */
@Singleton
class NoteDbChecks implements Checks {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ChangeData.Factory changeDataFactory;
  private final ChangeNotes.Factory changeNotesFactory;
  private final CheckNotes.Factory checkNotesFactory;
  private final Checkers checkers;
  private final PatchSetUtil psUtil;
  private final Provider<AnonymousUser> anonymousUserProvider;
  private final Provider<ChangeQueryBuilder> queryBuilderProvider;

  @Inject
  NoteDbChecks(
      ChangeData.Factory changeDataFactory,
      ChangeNotes.Factory changeNotesFactory,
      CheckNotes.Factory checkNotesFactory,
      Checkers checkers,
      PatchSetUtil psUtil,
      Provider<AnonymousUser> anonymousUserProvider,
      Provider<ChangeQueryBuilder> queryBuilderProvider) {
    this.changeDataFactory = changeDataFactory;
    this.changeNotesFactory = changeNotesFactory;
    this.checkers = checkers;
    this.psUtil = psUtil;
    this.checkNotesFactory = checkNotesFactory;
    this.anonymousUserProvider = anonymousUserProvider;
    this.queryBuilderProvider = queryBuilderProvider;
  }

  @Override
  public ImmutableList<Check> getChecks(Project.NameKey projectName, PatchSet.Id psId)
      throws OrmException, IOException {
    ChangeData cd = changeDataFactory.create(projectName, psId.getParentKey());
    PatchSet ps = cd.patchSet(psId);
    if (ps == null) {
      throw new OrmException("patch set not found: " + psId);
    }
    Map<CheckerUuid, Checker> checkers = activeAndValidCheckersForProject(projectName);

    CheckNotes checkNotes = checkNotesFactory.create(cd.change());
    checkNotes.load();

    ImmutableList.Builder<Check> result = ImmutableList.builder();
    for (Map.Entry<String, NoteDbCheck> e :
        checkNotes
            .getChecks()
            .getOrDefault(ps.getRevision(), NoteDbCheckMap.empty())
            .checks
            .entrySet()) {
      CheckerUuid checkerUuid = CheckerUuid.parse(e.getKey());
      if (checkers.remove(checkerUuid) != null) {
        result.add(e.getValue().toCheck(projectName, psId, checkerUuid));
      }
    }

    // All remaining checkers not mentioned in the notes need to be checked for relevance. Any
    // relevant checkers are reported as NOT_STARTED, with creation time matching the patch set.
    if (!checkers.isEmpty()) {
      ChangeQueryBuilder queryBuilder =
          queryBuilderProvider.get().asUser(anonymousUserProvider.get());
      for (Checker checker : checkers.values()) {
        if (matches(checker, cd, queryBuilder)) {
          // Add synthetic check at the creation time of the patch set.
          result.add(
              Check.builder(CheckKey.create(projectName, psId, checker.getUuid()))
                  .setState(CheckState.NOT_STARTED)
                  .setCreated(ps.getCreatedOn())
                  .setUpdated(ps.getCreatedOn())
                  .build());
        }
      }
    }
    // TODO(dborowitz): Sort? By updated? By UUID? By status?
    return result.build();
  }

  private static boolean matches(Checker checker, ChangeData cd, ChangeQueryBuilder queryBuilder)
      throws OrmException {
    if (!checker.getQuery().isPresent()) {
      return cd.change().isNew();
    }
    String query = checker.getQuery().get();
    Predicate<ChangeData> predicate;
    try {
      predicate = queryBuilder.parse(query);
    } catch (QueryParseException e) {
      logger.atWarning().withCause(e).log(
          "skipping invalid query for checker %s: %s", checker.getUuid(), query);
      return false;
    }
    if (!predicate.isMatchable()) {
      logger.atWarning().log(
          "skipping non-matchable query for checker %s: %s", checker.getUuid(), query);
      return false;
    }
    if (!hasStatusPredicate(predicate)) {
      predicate = predicate.and(ChangeStatusPredicate.open(), predicate);
    }
    return predicate.asMatchable().match(cd);
  }

  private static boolean hasStatusPredicate(Predicate<ChangeData> predicate) {
    if (predicate instanceof IndexPredicate) {
      return ((IndexPredicate<ChangeData>) predicate)
          .getField()
          .getName()
          .equals(ChangeField.STATUS.getName());
    }
    return predicate.getChildren().stream().anyMatch(NoteDbChecks::hasStatusPredicate);
  }

  @Override
  public Optional<Check> getCheck(CheckKey checkKey) throws OrmException, IOException {
    // TODO(gerrit-team): Instead of reading the complete notes map, read just one note.
    return getChecksAsStream(checkKey.project(), checkKey.patchSet())
        .filter(c -> c.key().checkerUuid().equals(checkKey.checkerUuid()))
        .findAny();
  }

  private Stream<Check> getChecksAsStream(Project.NameKey projectName, PatchSet.Id psId)
      throws OrmException, IOException {
    // TODO(gerrit-team): Instead of reading the complete notes map, read just one note.
    ChangeNotes notes = changeNotesFactory.create(projectName, psId.getParentKey());
    PatchSet patchSet = psUtil.get(notes, psId);
    CheckNotes checkNotes = checkNotesFactory.create(notes.getChange());

    checkNotes.load();
    Set<CheckerUuid> checkerUuids = activeAndValidCheckersForProject(projectName).keySet();
    return checkNotes
        .getChecks()
        .getOrDefault(patchSet.getRevision(), NoteDbCheckMap.empty())
        .checks
        .entrySet()
        .stream()
        .map(e -> e.getValue().toCheck(projectName, psId, CheckerUuid.parse(e.getKey())))
        .filter(c -> checkerUuids.contains(c.key().checkerUuid()));
  }

  /** Get all checkers that apply to a project. Might return a superset of checkers that apply. */
  private Map<CheckerUuid, Checker> activeAndValidCheckersForProject(Project.NameKey projectName)
      throws IOException {
    return checkers.checkersOf(projectName).stream().collect(toMap(Checker::getUuid, c -> c));
  }
}
