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

package com.google.gerrit.plugins.checks.db;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.Checks;
import com.google.gerrit.plugins.checks.acceptance.AbstractCheckersTest;
import com.google.gerrit.plugins.checks.api.CheckState;
import com.google.gerrit.plugins.checks.api.CheckerStatus;
import com.google.gerrit.plugins.checks.api.CombinedCheckState;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import org.eclipse.jgit.junit.TestRepository;
import org.junit.Before;
import org.junit.Test;

public class GetCombinedCheckStateIT extends AbstractCheckersTest {
  // Tests against Gerrit Java API once it exists.
  private Checks checks;
  private PatchSet.Id patchSetId;

  @Before
  public void setUp() throws Exception {
    checks = plugin.getHttpInjector().getInstance(Checks.class);

    patchSetId = createChange().getPatchSetId();
  }

  @Test
  public void returnsNotRelevantWhenNoCheck() throws Exception {
    Project.NameKey otherProject = new Project.NameKey("other-project");
    gApi.projects().create(otherProject.get());
    TestRepository<?> testRepository = cloneProject(otherProject);
    PushOneCommit.Result result =
        createChange(
            testRepository,
            "refs/heads/master",
            "Change in other-project",
            PushOneCommit.FILE_NAME,
            "content2",
            null);

    CombinedCheckState combinedCheckState =
        checks.getCombinedCheckState(otherProject, result.getPatchSetId());

    assertThat(combinedCheckState).isEqualTo(CombinedCheckState.NOT_RELEVANT);
  }

  @Test
  public void returnsNotRelevantWhenCheckerIsDisabled() throws Exception {
    CheckerUuid checkerUuid = newRequiredChecker().status(CheckerStatus.DISABLED).create();
    setCheckSuccessful(checkerUuid);

    CombinedCheckState combinedCheckState = checks.getCombinedCheckState(project, patchSetId);

    assertThat(combinedCheckState).isEqualTo(CombinedCheckState.NOT_RELEVANT);
  }

  @Test
  public void returnsFailedWhenAnyRequiredCheckerFailed() throws Exception {
    CheckerUuid checkerUuid = newRequiredChecker().create();
    setCheckSuccessful(checkerUuid);
    CheckerUuid otherCheckerUuid = newRequiredChecker().create();
    setCheckState(otherCheckerUuid, CheckState.FAILED);

    CombinedCheckState combinedCheckState = checks.getCombinedCheckState(project, patchSetId);

    assertThat(combinedCheckState).isEqualTo(CombinedCheckState.FAILED);
  }

  @Test
  public void returnsSuccessfulWhenAllRequiredCheckersSucceeded() throws Exception {
    CheckerUuid checkerUuid = newRequiredChecker().create();
    setCheckSuccessful(checkerUuid);
    CheckerUuid otherCheckerUuid = newRequiredChecker().create();
    setCheckSuccessful(otherCheckerUuid);

    CombinedCheckState combinedCheckState = checks.getCombinedCheckState(project, patchSetId);

    assertThat(combinedCheckState).isEqualTo(CombinedCheckState.SUCCESSFUL);
  }

  @Test
  public void returnsInProgressWithOnlyBackFilledCheck() throws Exception {
    newRequiredChecker().create();
    // No check state is created.

    CombinedCheckState combinedCheckState = checks.getCombinedCheckState(project, patchSetId);

    assertThat(combinedCheckState).isEqualTo(CombinedCheckState.IN_PROGRESS);
  }

  private void setCheckSuccessful(CheckerUuid checkerUuid) {
    setCheckState(checkerUuid, CheckState.SUCCESSFUL);
  }

  private void setCheckState(CheckerUuid checkerUuid, CheckState checkState) {
    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid);
    checkOperations.newCheck(checkKey).setState(checkState).upsert();
  }
}
