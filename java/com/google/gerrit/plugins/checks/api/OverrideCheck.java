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

import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.plugins.checks.AdministrateCheckersPermission;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Singleton;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Provider;

@Singleton
public class OverrideCheck implements RestModifyView<CheckResource, CheckOverrideInput> {

  private final Provider<CurrentUser> self;
  private final PermissionBackend permissionBackend;
  private final AdministrateCheckersPermission permission;

  @Inject
  OverrideCheck(
      Provider<CurrentUser> self,
      PermissionBackend permissionBackend,
      AdministrateCheckersPermission permission) {
    this.self = self;
    this.permissionBackend = permissionBackend;
    this.permission = permission;
  }

  @Override
  public Response<CheckInfo> apply(CheckResource checkResource, CheckOverrideInput input)
      throws AuthException, PermissionBackendException, BadRequestException,
          ResourceConflictException {
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
    throw new NotImplementedException();
  }
}
