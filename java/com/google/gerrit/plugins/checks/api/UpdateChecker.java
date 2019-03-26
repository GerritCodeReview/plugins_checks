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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSortedSet;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.plugins.checks.AdministrateCheckersPermission;
import com.google.gerrit.plugins.checks.Checker;
import com.google.gerrit.plugins.checks.CheckerJson;
import com.google.gerrit.plugins.checks.CheckerName;
import com.google.gerrit.plugins.checks.CheckerQuery;
import com.google.gerrit.plugins.checks.CheckerUpdate;
import com.google.gerrit.plugins.checks.CheckersUpdate;
import com.google.gerrit.plugins.checks.NoSuchCheckerException;
import com.google.gerrit.plugins.checks.UrlValidator;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.UserInitiated;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import javax.inject.Singleton;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class UpdateChecker implements RestModifyView<CheckerResource, CheckerInput> {
  private final PermissionBackend permissionBackend;
  private final Provider<CheckersUpdate> checkersUpdate;
  private final CheckerJson checkerJson;
  private final ProjectCache projectCache;

  private final AdministrateCheckersPermission permission;

  @Inject
  public UpdateChecker(
      PermissionBackend permissionBackend,
      @UserInitiated Provider<CheckersUpdate> checkersUpdate,
      CheckerJson checkerJson,
      AdministrateCheckersPermission permission,
      ProjectCache projectCache) {
    this.permissionBackend = permissionBackend;
    this.checkersUpdate = checkersUpdate;
    this.checkerJson = checkerJson;
    this.permission = permission;
    this.projectCache = projectCache;
  }

  @Override
  public CheckerInfo apply(CheckerResource resource, CheckerInput input)
      throws RestApiException, PermissionBackendException, NoSuchCheckerException, IOException,
          ConfigInvalidException {
    permissionBackend.currentUser().check(permission);

    CheckerUpdate.Builder checkerUpdateBuilder = CheckerUpdate.builder();

    // Callers shouldn't really be providing UUID. If they do, the only legal UUID is exactly the
    // current UUID.
    if (input.uuid != null && !input.uuid.equals(resource.getChecker().getUuid().get())) {
      throw new BadRequestException("uuid cannot be updated");
    }

    if (input.name != null) {
      String newName = CheckerName.clean(input.name);
      if (newName.isEmpty()) {
        throw new BadRequestException("name cannot be unset");
      }
      checkerUpdateBuilder.setName(newName);
    }

    if (input.description != null) {
      checkerUpdateBuilder.setDescription(Strings.nullToEmpty(input.description).trim());
    }

    if (input.url != null) {
      checkerUpdateBuilder.setUrl(UrlValidator.clean(input.url));
    }

    if (input.repository != null) {
      Project.NameKey repository = resolveRepository(input.repository);
      checkerUpdateBuilder.setRepository(repository);
    }

    if (input.status != null) {
      checkerUpdateBuilder.setStatus(input.status);
    }

    if (input.blockingConditions != null) {
      checkerUpdateBuilder.setBlockingConditions(
          ImmutableSortedSet.copyOf(input.blockingConditions));
    }

    if (input.query != null) {
      checkerUpdateBuilder.setQuery(CheckerQuery.clean(input.query));
    }

    Checker updatedChecker =
        checkersUpdate
            .get()
            .updateChecker(resource.getChecker().getUuid(), checkerUpdateBuilder.build());
    return checkerJson.format(updatedChecker);
  }

  private Project.NameKey resolveRepository(String repository)
      throws BadRequestException, UnprocessableEntityException, IOException {
    if (repository == null || repository.trim().isEmpty()) {
      throw new BadRequestException("repository cannot be unset");
    }

    ProjectState projectState = projectCache.checkedGet(new Project.NameKey(repository.trim()));
    if (projectState == null) {
      throw new UnprocessableEntityException(String.format("repository %s not found", repository));
    }

    return projectState.getNameKey();
  }
}
