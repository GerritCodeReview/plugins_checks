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

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.plugins.checks.Check;
import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.Checker;
import com.google.gerrit.plugins.checks.Checkers;
import com.google.gerrit.plugins.checks.Checks;
import com.google.gerrit.plugins.checks.api.CheckerStatus;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.Project.NameKey;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;

/** Class to read checks from NoteDb. */
@Singleton
class NoteDbChecks implements Checks {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ChangeNotes.Factory changeNotesFactory;
  private final PatchSetUtil psUtil;
  private final CheckNotes.Factory checkNotesFactory;
  private final Checkers checkers;

  @Inject
  NoteDbChecks(
      ChangeNotes.Factory changeNotesFactory,
      PatchSetUtil psUtil,
      CheckNotes.Factory checkNotesFactory,
      Checkers checkers) {
    this.changeNotesFactory = changeNotesFactory;
    this.psUtil = psUtil;
    this.checkNotesFactory = checkNotesFactory;
    this.checkers = checkers;
  }

  @Override
  public ImmutableList<Check> getChecks(Project.NameKey projectName, PatchSet.Id psId)
      throws OrmException {
    ImmutableList.Builder<Check> result = ImmutableList.builder();
    for (Map.Entry<String, NoteDbCheck> e : getCheckEntries(projectName, psId)) {
      Check check = toCheck(e, projectName, psId);
      if (checkerEnabled(check)) {
        result.add(check);
      }
    }
    return result.build();
  }

  @Override
  public Optional<Check> getCheck(CheckKey checkKey) throws OrmException {
    // TODO(gerrit-team): Instead of reading the complete notes map, read just one note.
    String checkerUuidString = checkKey.checkerUuid();
    for (Map.Entry<String, NoteDbCheck> e :
        getCheckEntries(checkKey.project(), checkKey.patchSet())) {
      Check check = toCheck(e, checkKey.project(), checkKey.patchSet());
      if (e.getKey().equals(checkerUuidString) && checkerEnabled(check)) {
        return Optional.of(check);
      }
    }
    return Optional.empty();
  }

  private Set<Map.Entry<String, NoteDbCheck>> getCheckEntries(
      Project.NameKey projectName, PatchSet.Id psId) throws OrmException {
    ChangeNotes notes = changeNotesFactory.create(projectName, psId.getParentKey());
    PatchSet patchSet = psUtil.get(notes, psId);
    CheckNotes checkNotes = checkNotesFactory.create(notes.getChange());
    checkNotes.load();
    return checkNotes
        .getChecks()
        .getOrDefault(patchSet.getRevision(), NoteDbCheckMap.empty())
        .checks
        .entrySet();
  }

  private boolean checkerEnabled(Check check) throws OrmException {
    try {
      Optional<Checker> checker = checkers.getChecker(check.key().checkerUuid());
      return checker.isPresent() && checker.get().getStatus() == CheckerStatus.ENABLED;
    } catch (ConfigInvalidException e) {
      logger.atInfo().withCause(e).log(
          "ignoring checker %s because checker config is invalid", check.key());
      return false;
    } catch (IOException e) {
      throw new OrmException(e);
    }
  }

  private Check toCheck(Map.Entry<String, NoteDbCheck> e, NameKey projectName, PatchSet.Id psId) {
    return e.getValue().toCheck(projectName, psId, e.getKey());
  }
}
