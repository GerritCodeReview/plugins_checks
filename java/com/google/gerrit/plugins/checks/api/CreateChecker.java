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
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.DuplicateKeyException;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestCollectionModifyView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.plugins.checks.AdministrateCheckersPermission;
import com.google.gerrit.plugins.checks.Checker;
import com.google.gerrit.plugins.checks.CheckerCreation;
import com.google.gerrit.plugins.checks.CheckerJson;
import com.google.gerrit.plugins.checks.CheckerName;
import com.google.gerrit.plugins.checks.CheckerQuery;
import com.google.gerrit.plugins.checks.CheckerUpdate;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.CheckersUpdate;
import com.google.gerrit.plugins.checks.UrlValidator;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.UserInitiated;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class CreateChecker
    implements RestCollectionModifyView<TopLevelResource, CheckerResource, CheckerInput> {
  private final Provider<CurrentUser> self;
  private final PermissionBackend permissionBackend;
  private final Provider<CheckerQuery> checkerQueryProvider;
  private final Provider<CheckersUpdate> checkersUpdate;
  private final CheckerJson checkerJson;
  private final AdministrateCheckersPermission permission;
  private final ProjectCache projectCache;

  @Inject
  public CreateChecker(
      Provider<CurrentUser> self,
      PermissionBackend permissionBackend,
      Provider<CheckerQuery> checkerQueryProvider,
      @UserInitiated Provider<CheckersUpdate> checkersUpdate,
      CheckerJson checkerJson,
      AdministrateCheckersPermission permission,
      ProjectCache projectCache) {
    this.self = self;
    this.permissionBackend = permissionBackend;
    this.checkerQueryProvider = checkerQueryProvider;
    this.checkersUpdate = checkersUpdate;
    this.checkerJson = checkerJson;
    this.permission = permission;
    this.projectCache = projectCache;
  }

  @Override
  public Response<CheckerInfo> apply(TopLevelResource parentResource, CheckerInput input)
      throws RestApiException, PermissionBackendException, IOException, ConfigInvalidException,
          StorageException {
    if (!self.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }
    permissionBackend.currentUser().check(permission);

    if (input == null) {
      input = new CheckerInput();
    }

    if (Strings.isNullOrEmpty(input.uuid)) {
      throw new BadRequestException("uuid is required");
    }
    String uuidStr = input.uuid;
    CheckerUuid checkerUuid =
        CheckerUuid.tryParse(input.uuid)
            .orElseThrow(() -> new BadRequestException("invalid uuid: " + uuidStr));

    String name = CheckerName.clean(input.name);
    if (name.isEmpty()) {
      throw new BadRequestException("name is required");
    }

    Project.NameKey repository = resolveRepository(input.repository);

    CheckerCreation.Builder checkerCreationBuilder =
        CheckerCreation.builder()
            .setCheckerUuid(checkerUuid)
            .setName(name)
            .setRepository(repository);
    CheckerUpdate.Builder checkerUpdateBuilder = CheckerUpdate.builder();

    if (input.description != null && !input.description.trim().isEmpty()) {
      checkerUpdateBuilder.setDescription(input.description.trim());
    }
    if (input.url != null) {
      checkerUpdateBuilder.setUrl(UrlValidator.clean(input.url));
    }
    if (input.status != null) {
      checkerUpdateBuilder.setStatus(input.status);
    }
    if (input.required != null) {
      checkerUpdateBuilder.setRequired(input.required);
    }
    if (input.query != null) {
      checkerUpdateBuilder.setQuery(validateQuery(checkerUuid, repository, input.query));
    }
    try {
      Checker checker =
          checkersUpdate
              .get()
              .createChecker(checkerCreationBuilder.build(), checkerUpdateBuilder.build());
      return Response.created(checkerJson.format(checker));
    } catch (DuplicateKeyException e) {
      throw new ResourceConflictException(e.getMessage());
    }
  }

  private Project.NameKey resolveRepository(String repository)
      throws BadRequestException, UnprocessableEntityException, IOException {
    if (repository == null || repository.trim().isEmpty()) {
      throw new BadRequestException("repository is required");
    }

    ProjectState projectState = projectCache.checkedGet(Project.nameKey(repository.trim()));
    if (projectState == null) {
      throw new UnprocessableEntityException(String.format("repository %s not found", repository));
    }

    return projectState.getNameKey();
  }

  private String validateQuery(CheckerUuid checkerUuid, Project.NameKey repository, String query)
      throws BadRequestException, StorageException {
    try {
      return checkerQueryProvider.get().validate(checkerUuid, repository, query);
    } catch (ConfigInvalidException e) {
      throw new BadRequestException(e.getMessage(), e);
    }
  }
}
