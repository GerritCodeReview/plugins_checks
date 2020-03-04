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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.plugins.checks.AdministrateCheckersPermission;
import com.google.gerrit.plugins.checks.Check;
import com.google.gerrit.plugins.checks.CheckJson;
import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.CheckUpdate;
import com.google.gerrit.plugins.checks.Checks;
import com.google.gerrit.plugins.checks.Checks.GetCheckOptions;
import com.google.gerrit.plugins.checks.ChecksUpdate;
import com.google.gerrit.plugins.checks.ListChecksOption;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.UserInitiated;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Provider;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class OverrideCheck implements RestModifyView<CheckResource, CheckOverrideInput> {

  private final Provider<CurrentUser> self;
  private final PermissionBackend permissionBackend;
  private final AdministrateCheckersPermission permission;
  private final Checks checks;
  private final Provider<ChecksUpdate> checksUpdate;
  private final CheckJson.Factory checkJsonFactory;

  @Inject
  OverrideCheck(
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

  @Override
  public Response<CheckInfo> apply(CheckResource checkResource, CheckOverrideInput input)
      throws AuthException, PermissionBackendException, BadRequestException,
          ResourceConflictException, ConfigInvalidException, IOException {
    if (!self.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }
    permissionBackend.currentUser().check(permission);

    if (input.reason == null || input.reason.trim() == "") {
      throw new BadRequestException("Override must have reason.");
    }
    if (input.reason.length() > 300) {
      throw new BadRequestException(
          "Reason for override is too long. It must be up to 300 characters.");
    }
    if (checkResource.getCheck().overrides() != null
        && checkResource.getCheck().overrides().stream()
            .map(override -> override.overrider)
            .collect(Collectors.toList())
            .contains(self.get().getAccountId().get())) {
      throw new ResourceConflictException("The same user can't override the same check twice.");
    }
    CheckKey key =
        CheckKey.create(
            checkResource.getRevisionResource().getProject(),
            checkResource.getRevisionResource().getPatchSet().id(),
            checkResource.getCheckerUuid());

    CheckOverride checkOverride = createCheckOverride(input);

    CheckUpdate checkUpdate = CheckUpdate.builder().setNewOverride(checkOverride).build();

    Optional<Check> check = checks.getCheck(key, GetCheckOptions.defaults());
    Check updatedCheck;
    if (!check.isPresent()) {
      updatedCheck =
          checksUpdate.get().createCheck(key, checkUpdate, input.notify, input.notifyDetails);
    } else {
      updatedCheck =
          checksUpdate.get().updateCheck(key, checkUpdate, input.notify, input.notifyDetails);
    }
    return Response.ok(
        checkJsonFactory
            .create(ImmutableList.of(ListChecksOption.SUBMIT_IMPACT))
            .format(updatedCheck));
  }

  private CheckOverride createCheckOverride(CheckOverrideInput input) {
    CheckOverride checkOverride = new CheckOverride();
    checkOverride.overrider = self.get().getAccountId().get();
    checkOverride.reason = input.reason;
    checkOverride.overriddenOn = TimeUtil.nowTs();
    return checkOverride;
  }
}
