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

import com.google.common.collect.ImmutableSortedSet;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.Checks;
import com.google.gerrit.plugins.checks.acceptance.AbstractCheckersTest;
import com.google.gerrit.plugins.checks.api.BlockingCondition;
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

  private CheckerUuid checkerUuid;
  private PatchSet.Id patchSetId;

  @Before
  public void setUp() throws Exception {
    checks = plugin.getHttpInjector().getInstance(Checks.class);

    patchSetId = createChange().getPatchSetId();

    checkerUuid =
        checkerOperations
            .newChecker()
            .repository(project)
            .blockingConditions(BlockingCondition.STATE_NOT_PASSING)
            .status(CheckerStatus.ENABLED)
            .create();

    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid);
    checkOperations.newCheck(checkKey).setState(CheckState.SUCCESSFUL).upsert();
  }

  @Test
  public void skipsNotRequiredChecker() throws Exception {
    checkerOperations
        .checker(checkerUuid)
        .forUpdate()
        .blockingConditions(ImmutableSortedSet.of())
        .update();

    CombinedCheckState combinedCheckState =
        checks.getCombinedCheckState(project, patchSetId, false);

    assertThat(combinedCheckState).isEqualTo(CombinedCheckState.NOT_RELEVANT);
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
        checks.getCombinedCheckState(otherProject, result.getPatchSetId(), true);

    assertThat(combinedCheckState).isEqualTo(CombinedCheckState.NOT_RELEVANT);
  }

  @Test
  public void returnsNotRelevantWhenCheckerIsDisabled() throws Exception {
    checkerOperations.checker(checkerUuid).forUpdate().disable().update();

    CombinedCheckState combinedCheckState = checks.getCombinedCheckState(project, patchSetId, true);

    assertThat(combinedCheckState).isEqualTo(CombinedCheckState.NOT_RELEVANT);
  }

  @Test
  public void returnsFailedWhenAnyRequiredCheckerFailed() throws Exception {
    CheckerUuid otherCheckerUuid =
        checkerOperations
            .newChecker()
            .repository(project)
            .blockingConditions(BlockingCondition.STATE_NOT_PASSING)
            .status(CheckerStatus.ENABLED)
            .create();
    CheckKey otherCheckKey = CheckKey.create(project, patchSetId, otherCheckerUuid);
    checkOperations.newCheck(otherCheckKey).setState(CheckState.FAILED).upsert();

    CombinedCheckState combinedCheckState = checks.getCombinedCheckState(project, patchSetId, true);

    assertThat(combinedCheckState).isEqualTo(CombinedCheckState.FAILED);
  }

  @Test
  public void returnsFailedWhenAllRequiredCheckersSucceeded() throws Exception {
    CheckerUuid otherCheckerUuid =
        checkerOperations
            .newChecker()
            .repository(project)
            .blockingConditions(BlockingCondition.STATE_NOT_PASSING)
            .status(CheckerStatus.ENABLED)
            .create();
    CheckKey otherCheckKey = CheckKey.create(project, patchSetId, otherCheckerUuid);
    checkOperations.newCheck(otherCheckKey).setState(CheckState.SUCCESSFUL).upsert();

    CombinedCheckState combinedCheckState = checks.getCombinedCheckState(project, patchSetId, true);

    assertThat(combinedCheckState).isEqualTo(CombinedCheckState.SUCCESSFUL);
  }
}
