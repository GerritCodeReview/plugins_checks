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

import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestCollectionModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.plugins.checks.api.CheckInfo;
import com.google.gerrit.plugins.checks.api.CheckInput;
import com.google.gerrit.plugins.checks.api.CheckResource;
import com.google.gerrit.server.UserInitiated;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gwtorm.server.OrmException;
import java.io.IOException;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class PostCheck
    implements RestCollectionModifyView<RevisionResource, CheckResource, CheckInput> {
  private final PermissionBackend permissionBackend;
  private final AdministrateCheckersPermission permission;
  private final Checkers checkers;
  private final Checks checks;
  private final Provider<ChecksUpdate> checksUpdate;
  private final CheckJson.Factory checkJsonFactory;

  @Inject
  PostCheck(
      PermissionBackend permissionBackend,
      AdministrateCheckersPermission permission,
      Checkers checkers,
      Checks checks,
      @UserInitiated Provider<ChecksUpdate> checksUpdate,
      CheckJson.Factory checkJsonFactory) {
    this.permissionBackend = permissionBackend;
    this.permission = permission;
    this.checkers = checkers;
    this.checks = checks;
    this.checksUpdate = checksUpdate;
    this.checkJsonFactory = checkJsonFactory;
  }

  @Override
  public CheckInfo apply(RevisionResource rsrc, CheckInput input)
      throws OrmException, IOException, RestApiException, PermissionBackendException,
          ConfigInvalidException {
    if (input == null) {
      input = new CheckInput();
    }
    if (input.checkerUuid == null) {
      throw new BadRequestException("checker UUID is required");
    }
    if (!CheckerUuid.isUuid(input.checkerUuid)) {
      throw new BadRequestException(String.format("invalid checker UUID: %s", input.checkerUuid));
    }

    permissionBackend.currentUser().check(permission);

    CheckerUuid checkerUuid = CheckerUuid.parse(input.checkerUuid);

    CheckKey key = CheckKey.create(rsrc.getProject(), rsrc.getPatchSet().getId(), checkerUuid);
    Optional<Check> check = checks.getCheck(key);
    Check updatedCheck;
    if (!check.isPresent()) {
      checkers
          .getChecker(checkerUuid)
          .orElseThrow(
              () ->
                  new UnprocessableEntityException(
                      String.format("checker %s not found", checkerUuid)));

      if (input.state == null) {
        throw new BadRequestException("state is required on creation");
      }

      updatedCheck = checksUpdate.get().createCheck(key, toCheckUpdate(input));
    } else {
      updatedCheck = checksUpdate.get().updateCheck(key, toCheckUpdate(input));
    }
    return checkJsonFactory.noOptions().format(updatedCheck);
  }

  private static CheckUpdate toCheckUpdate(CheckInput input) throws BadRequestException {
    return CheckUpdate.builder()
        .setState(Optional.ofNullable(input.state))
        .setUrl(input.url == null ? Optional.empty() : Optional.of(CheckerUrl.clean(input.url)))
        .setStarted(Optional.ofNullable(input.started))
        .setFinished(Optional.ofNullable(input.finished))
        .build();
  }
}
