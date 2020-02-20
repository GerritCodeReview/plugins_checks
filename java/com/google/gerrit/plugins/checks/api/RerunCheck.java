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

import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.plugins.checks.Check;
import com.google.gerrit.plugins.checks.CheckJson;
import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.CheckUpdate;
import com.google.gerrit.plugins.checks.Checker;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.Checkers;
import com.google.gerrit.plugins.checks.Checks;
import com.google.gerrit.plugins.checks.Checks.GetCheckOptions;
import com.google.gerrit.plugins.checks.ChecksUpdate;
import com.google.gerrit.plugins.checks.events.RerunCheckEvent;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.UserInitiated;
import com.google.gerrit.server.events.EventDispatcher;
import com.google.gerrit.server.events.EventFactory;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;
import javax.inject.Provider;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class RerunCheck implements RestModifyView<CheckResource, RerunInput> {
  private final Provider<CurrentUser> self;
  private final Checks checks;
  private final Provider<ChecksUpdate> checksUpdate;
  private final CheckJson.Factory checkJsonFactory;
  private final Checkers checkers;
  private final DynamicItem<EventDispatcher> eventDispatcher;
  private final EventFactory eventFactory;

  @Inject
  RerunCheck(
      Provider<CurrentUser> self,
      Checks checks,
      @UserInitiated Provider<ChecksUpdate> checksUpdate,
      CheckJson.Factory checkJsonFactory,
      Checkers checkers,
      DynamicItem<EventDispatcher> eventDispatcher,
      EventFactory eventFactory) {
    this.self = self;
    this.checks = checks;
    this.checksUpdate = checksUpdate;
    this.checkJsonFactory = checkJsonFactory;
    this.checkers = checkers;
    this.eventDispatcher = eventDispatcher;
    this.eventFactory = eventFactory;
  }

  @Override
  public Response<CheckInfo> apply(CheckResource checkResource, RerunInput input)
      throws RestApiException, IOException, PermissionBackendException, ConfigInvalidException {
    if (!self.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }
    if (checkResource.getRevisionResource().getEdit().isPresent()) {
      throw new ResourceConflictException("checks are not supported on a change edit");
    }
    if (input == null) {
      input = new RerunInput();
    }
    CheckKey key =
        CheckKey.create(
            checkResource.getRevisionResource().getProject(),
            checkResource.getRevisionResource().getPatchSet().id(),
            checkResource.getCheckerUuid());
    Optional<Check> check = checks.getCheck(key, GetCheckOptions.defaults());
    CheckerUuid checkerUuid = checkResource.getCheckerUuid();
    Check updatedCheck;
    if (!check.isPresent()) {
      Checker checker =
          checkers
              .getChecker(checkerUuid)
              .orElseThrow(
                  () ->
                      new ResourceNotFoundException(
                          String.format("checker %s not found", checkerUuid)));
      // This error should not be thrown since this case is filtered before reaching this code.
      // Also return a backfilled check for checkers that do not apply to the change.
      updatedCheck =
          Check.newBackfilledCheck(
              checkResource.getRevisionResource().getProject(),
              checkResource.getRevisionResource().getPatchSet(),
              checker);
    } else {
      CheckUpdate.Builder builder = CheckUpdate.builder();
      builder
          .setState(CheckState.NOT_STARTED)
          .unsetFinished()
          .unsetStarted()
          .setMessage("")
          .setUrl("");
      updatedCheck =
          checksUpdate.get().updateCheck(key, builder.build(), input.notify, input.notifyDetails);

      RerunCheckEvent rerunCheckEvent = new RerunCheckEvent();
      rerunCheckEvent.projectName = checkResource.getRevisionResource().getProject().getName();
      rerunCheckEvent.checkerUuid = key.checkerUuid().get();
      eventDispatcher.get().postEvent(rerunCheckEvent);
    }
    return Response.ok(checkJsonFactory.noOptions().format(updatedCheck));
  }
}
