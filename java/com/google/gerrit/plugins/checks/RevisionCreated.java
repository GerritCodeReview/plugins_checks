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

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.extensions.events.RevisionCreatedListener;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.plugins.checks.Checks.GetCheckOptions;
import com.google.gerrit.server.UserInitiated;
import java.io.IOException;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class RevisionCreated implements RevisionCreatedListener {

  private final Checks checks;
  private final Checkers checkers;
  private final Provider<ChecksUpdate> checksUpdate;
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Inject
  RevisionCreated(
      Checks checks, Checkers checkers, @UserInitiated Provider<ChecksUpdate> checksUpdate) {
    this.checks = checks;
    this.checkers = checkers;
    this.checksUpdate = checksUpdate;
  }

  @Override
  public void onRevisionCreated(Event event) {
    ChangeInfo changeInfo = event.getChange();
    RevisionInfo revisionInfo = event.getRevision();
    Change.Id changeId = Change.id(changeInfo._number);
    PatchSet.Id patchId = PatchSet.id(changeId, revisionInfo._number);
    PatchSet.Id previousPatchId = PatchSet.id(changeId, revisionInfo._number - 1);
    try {
      ImmutableList<Check> previousCheckList =
          checks.getChecks(
              Project.nameKey(changeInfo.project), previousPatchId, GetCheckOptions.defaults());
      for (Check check : previousCheckList) {
        CheckerUuid checkerUuid = check.key().checkerUuid();
        Checker checker = checkers.getChecker(checkerUuid).get();
        if (checker.getCopyPolicy().contains(revisionInfo.kind)
            && check.state().isInProgress() == false) {
          CheckKey key =
              CheckKey.create(Project.NameKey.parse(changeInfo.project), patchId, checkerUuid);
          CheckUpdate.Builder checkUpdate = CheckUpdate.builder().setState(check.state());
          check.message().ifPresent(checkUpdate::setMessage);
          check.url().ifPresent(checkUpdate::setUrl);
          check.finished().ifPresent(checkUpdate::setFinished);
          check.started().ifPresent(checkUpdate::setStarted);
          checksUpdate.get().createCheck(key, checkUpdate.build(), NotifyHandling.NONE, null);
        }
      }
    } catch (IOException iox) {
      logger.atSevere().withCause(iox).log("Error reading from database %s", iox.getMessage());
    } catch (ConfigInvalidException cix) {
      logger.atSevere().withCause(cix).log("Invalid checker %s", cix.getMessage());
    } catch (BadRequestException bre) {
      logger.atSevere().withCause(bre).log("Bad Request Exception %s", bre.getMessage());
    }
  }
}
