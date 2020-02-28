// Copyright (C) 2020 The Android Open Source Project
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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.plugins.checks.CheckerRef;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.inject.Provider;
import java.io.IOException;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefRename;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;

@Singleton
public class CheckerRefMigration {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final GitRepositoryManager repoManager;
  private final AllProjectsName allProjectsName;
  private final BatchUpdate.Factory updateFactory;
  private final Provider<CurrentUser> user;

  private static final String TMP_REF = "refs/tmp/checker-migration";

  @Inject
  CheckerRefMigration(
      GitRepositoryManager repoManager,
      AllProjectsName allProjectsName,
      BatchUpdate.Factory updateFactory,
      Provider<CurrentUser> user) {
    this.repoManager = repoManager;
    this.allProjectsName = allProjectsName;
    this.updateFactory = updateFactory;
    this.user = user;
  }

  public void migrate() {
    try (Repository repo = repoManager.openRepository(allProjectsName)) {

      // This part is specifically for cases where the rename failed half-way last time.
      Ref ref = repo.exactRef(TMP_REF);
      if (ref != null) {
        renameRef(TMP_REF, CheckerRef.REFS_META_CHECKERS, repo);
        return;
      }

      ref = repo.exactRef(CheckerRef.LEGACY_REFS_META_CHECKERS);
      if (ref == null) {
        return;
      }
      renameRef(CheckerRef.LEGACY_REFS_META_CHECKERS, TMP_REF, repo);
      renameRef(TMP_REF, CheckerRef.REFS_META_CHECKERS, repo);
    } catch (Exception ex) {
      // ignore, if failed for any reason it shouldn't stop the REST endpoint from working.
      logger.atWarning().withCause(ex).log(
          "Failed to migrate refs/meta/checkers/ to refs/meta/checkers (without trailing slash");
    }
  }

  private void renameRef(String currentName, String targetName, Repository repo)
      throws IOException, RestApiException {
    RefRename refRename = repo.renameRef(currentName, targetName);
    Result result = refRename.rename();
    if (result != Result.RENAMED) {
      throw new ResourceConflictException(
          String.format(
              "Rename of %s to %s failed with the failure: %s",
              currentName, targetName, result.name()));
    }
    RefUpdate update = repo.updateRef(currentName);
    update.setExpectedOldObjectId(repo.exactRef(currentName).getObjectId());
    update.setNewObjectId(ObjectId.zeroId());
    update.setForceUpdate(true);
    update.delete();
  }
}
