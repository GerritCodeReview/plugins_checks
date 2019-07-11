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

import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.plugins.checks.AdministrateCheckersPermission;
import com.google.gerrit.plugins.checks.Check;
import com.google.gerrit.plugins.checks.CheckJson;
import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.CheckUpdate;
import com.google.gerrit.plugins.checks.Checks;
import com.google.gerrit.plugins.checks.Checks.GetCheckOptions;
import com.google.gerrit.plugins.checks.ChecksUpdate;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.UserInitiated;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Optional;
import javax.inject.Provider;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class RerunCheck implements RestModifyView<CheckResource, CheckInput> {
  private final Provider<CurrentUser> self;
  private final PermissionBackend permissionBackend;
  private final AdministrateCheckersPermission permission;
  private final Checks checks;
  private final Provider<ChecksUpdate> checksUpdate;
  private final CheckJson.Factory checkJsonFactory;

  @Inject
  RerunCheck(
      Provider<CurrentUser> self,
      PermissionBackend permissionBackend,
      AdministrateCheckersPermission permission,
      Checks checks,
      @UserInitiated Provider<ChecksUpdate> checksUpdate,
      CheckJson.Factory checkJsonFactory) {
    this.self = self;
    this.permissionBackend = permissionBackend;
    this.permission = permission;
    this.checks = checks;
    this.checksUpdate = checksUpdate;
    this.checkJsonFactory = checkJsonFactory;
  }

  public CheckInfo apply(CheckResource checkResource, CheckInput input)
      throws RestApiException, IOException, StorageException, PermissionBackendException,
          ConfigInvalidException {
    if (!self.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }
    permissionBackend.currentUser().check(permission);
    if (checkResource.getRevisionResource().getEdit().isPresent()) {
      throw new ResourceConflictException("checks are not supported on a change edit");
    }
    CheckKey key =
        CheckKey.create(
            checkResource.getRevisionResource().getProject(),
            checkResource.getRevisionResource().getPatchSet().id(),
            checkResource.getCheckerUuid());
    Optional<Check> check = checks.getCheck(key, GetCheckOptions.defaults());
    if (!check.isPresent()) {
      throw new UnprocessableEntityException(String.format("check %s not found", key));
    }
    CheckUpdate.Builder builder = CheckUpdate.builder();
    builder
        .setState(CheckState.NOT_STARTED)
        .setFinished(new Timestamp(0))
        .setStarted(new Timestamp(0));
    Check updatedCheck = checksUpdate.get().updateCheck(key, builder.build());
    return checkJsonFactory.noOptions().format(updatedCheck);
  }
}
