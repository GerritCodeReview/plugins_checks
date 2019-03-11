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

package com.google.gerrit.plugins.checks.acceptance.api;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.acceptance.AbstractCheckersTest;
import com.google.gerrit.plugins.checks.api.CheckInfo;
import com.google.gerrit.plugins.checks.api.CheckInput;
import com.google.gerrit.plugins.checks.api.CheckState;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.inject.Inject;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;

public class UpdateCheckIT extends AbstractCheckersTest {
  @Inject private RequestScopeOperations requestScopeOperations;

  private PatchSet.Id patchSetId;
  private CheckKey checkKey;

  @Before
  public void setUp() throws Exception {
    patchSetId = createChange().getPatchSetId();

    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    checkKey = CheckKey.create(project, patchSetId, checkerUuid);
    checkOperations.newCheck(checkKey).setState(CheckState.RUNNING).upsert();
  }

  @Test
  public void updateCheckState() throws Exception {
    CheckInput input = new CheckInput();
    input.state = CheckState.FAILED;

    CheckInfo info = checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).update(input);
    assertThat(info.state).isEqualTo(CheckState.FAILED);
  }

  @Test
  public void canUpdateCheckForDisabledChecker() throws Exception {
    checkerOperations.checker(checkKey.checkerUuid()).forUpdate().disable().update();

    CheckInput input = new CheckInput();
    input.state = CheckState.SUCCESSFUL;

    CheckInfo info = checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).update(input);
    assertThat(info.state).isEqualTo(CheckState.SUCCESSFUL);
  }

  @Test
  public void canUpdateCheckForCheckerThatDoesNotApplyToTheProject() throws Exception {
    Project.NameKey otherProject = createProjectOverAPI("other", null, true, null);
    checkerOperations.checker(checkKey.checkerUuid()).forUpdate().repository(otherProject).update();

    CheckInput input = new CheckInput();
    input.state = CheckState.SUCCESSFUL;

    CheckInfo info = checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).update(input);
    assertThat(info.state).isEqualTo(CheckState.SUCCESSFUL);
  }

  @Test
  public void canUpdateCheckForCheckerThatDoesNotApplyToTheChange() throws Exception {
    checkerOperations
        .checker(checkKey.checkerUuid())
        .forUpdate()
        .query("message:not-matching")
        .update();

    CheckInput input = new CheckInput();
    input.state = CheckState.SUCCESSFUL;

    CheckInfo info = checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).update(input);
    assertThat(info.state).isEqualTo(CheckState.SUCCESSFUL);
  }

  @Test
  public void canUpdateCheckForNonExistingChecker() throws Exception {
    deleteCheckerRef(checkKey.checkerUuid());

    CheckInput input = new CheckInput();
    input.state = CheckState.SUCCESSFUL;

    CheckInfo info = checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).update(input);
    assertThat(info.state).isEqualTo(CheckState.SUCCESSFUL);
  }

  @Test
  public void canUpdateCheckForInvalidChecker() throws Exception {
    checkerOperations.checker(checkKey.checkerUuid()).forUpdate().forceInvalidConfig().update();

    CheckInput input = new CheckInput();
    input.state = CheckState.SUCCESSFUL;

    CheckInfo info = checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).update(input);
    assertThat(info.state).isEqualTo(CheckState.SUCCESSFUL);
  }

  @Test
  public void cannotUpdateCheckWithoutAdministrateCheckers() throws Exception {
    requestScopeOperations.setApiUser(user.getId());

    exception.expect(AuthException.class);
    exception.expectMessage("not permitted");
    checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).update(new CheckInput());
  }

  private void deleteCheckerRef(CheckerUuid checkerUuid) throws Exception {
    try (Repository allProjectsRepo = repoManager.openRepository(allProjects)) {
      TestRepository<InMemoryRepository> testRepo =
          new TestRepository<>((InMemoryRepository) allProjectsRepo);
      RefUpdate ru = testRepo.getRepository().updateRef(checkerUuid.toRefName(), true);
      ru.setForceUpdate(true);
      assertThat(ru.delete()).isEqualTo(RefUpdate.Result.FORCED);
    }
  }
}
