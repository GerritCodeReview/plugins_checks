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

package com.google.gerrit.plugins.checks.acceptance.rules;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSortedSet;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.Sandboxed;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.acceptance.AbstractCheckersTest;
import com.google.gerrit.plugins.checks.api.BlockingCondition;
import com.google.gerrit.plugins.checks.api.CheckState;
import com.google.gerrit.plugins.checks.api.CheckerStatus;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import org.junit.Before;
import org.junit.Test;

public class ChecksSubmitRuleIT extends AbstractCheckersTest {

  private String testChangeId;
  private PatchSet.Id testPatchSetId;
  private CheckerUuid testCheckerUuid;

  @Before
  public void setUp() throws Exception {
    PushOneCommit.Result result = createChange();
    result.assertOkStatus();
    testPatchSetId = result.getPatchSetId();
    testChangeId = result.getChangeId();

    // Approves "Code-Review" label so that the change only needs to meet the submit requirements
    // about checks.
    approve(testChangeId);

    // Creates a test Checker which is enabled and required for the test repository.
    testCheckerUuid =
        checkerOperations
            .newChecker()
            .repository(project)
            .blockingConditions(BlockingCondition.STATE_NOT_PASSING)
            .status(CheckerStatus.ENABLED)
            .create();
  }

  @Test
  public void nonApplicableCheckerNotBlockingSubmit() throws Exception {
    postCheckResult(testCheckerUuid, CheckState.FAILED);
    // Updates the checker so that it isn't applicable to the change any more.
    Project.NameKey otherRepo = new Project.NameKey("other-project");
    gApi.projects().create(otherRepo.get());
    checkerOperations.checker(testCheckerUuid).forUpdate().repository(otherRepo).update();

    gApi.changes().id(testChangeId).current().submit();

    assertThat(gApi.changes().id(testChangeId).get().status).isEqualTo(ChangeStatus.MERGED);
  }

  @Test
  public void disabledCheckerDoesNotBlockingSubmit() throws Exception {
    postCheckResult(testCheckerUuid, CheckState.FAILED);
    checkerOperations.checker(testCheckerUuid).forUpdate().disable().update();

    gApi.changes().id(testChangeId).current().submit();

    assertThat(gApi.changes().id(testChangeId).get().status).isEqualTo(ChangeStatus.MERGED);
  }

  @Test
  public void enabledCheckerNotBlockingSubmitIfNoBlockingCondition() throws Exception {
    postCheckResult(testCheckerUuid, CheckState.FAILED);
    checkerOperations
        .checker(testCheckerUuid)
        .forUpdate()
        .blockingConditions(ImmutableSortedSet.of())
        .update();

    gApi.changes().id(testChangeId).current().submit();

    assertThat(gApi.changes().id(testChangeId).get().status).isEqualTo(ChangeStatus.MERGED);
  }

  @Test
  public void enabledCheckerNotBlockingSubmitIfNotInBlockingState() throws Exception {
    postCheckResult(testCheckerUuid, CheckState.SUCCESSFUL);

    gApi.changes().id(testChangeId).current().submit();

    assertThat(gApi.changes().id(testChangeId).get().status).isEqualTo(ChangeStatus.MERGED);
  }

  @Test
  public void enabledCheckerBlockingSubmitIfInBlockingState() throws Exception {
    postCheckResult(testCheckerUuid, CheckState.FAILED);

    exception.expect(ResourceConflictException.class);
    exception.expectMessage("Passing all blocking checks required");
    gApi.changes().id(testChangeId).current().submit();
  }

  @Test
  @Sandboxed
  public void multipleCheckerBlockingSubmit() throws Exception {
    // Two enabled and required checkers. They are blocking if any of them isn't passing.
    CheckerUuid testCheckerUuid2 =
        checkerOperations
            .newChecker()
            .repository(project)
            .blockingConditions(BlockingCondition.STATE_NOT_PASSING)
            .create();
    postCheckResult(testCheckerUuid, CheckState.SUCCESSFUL);
    postCheckResult(testCheckerUuid2, CheckState.FAILED);

    exception.expect(ResourceConflictException.class);
    exception.expectMessage("Passing all blocking checks required");
    gApi.changes().id(testChangeId).current().submit();
  }

  @Test
  @Sandboxed
  public void multipleCheckerNotBlockingSubmit() throws Exception {
    // Two enabled checkers. The one failed doesn't block because it's not required.
    CheckerUuid testCheckerUuidDisabled =
        checkerOperations
            .newChecker()
            .repository(project)
            .blockingConditions(ImmutableSortedSet.of())
            .create();
    postCheckResult(testCheckerUuidDisabled, CheckState.FAILED);
    postCheckResult(testCheckerUuid, CheckState.SUCCESSFUL);

    gApi.changes().id(testChangeId).current().submit();

    assertThat(gApi.changes().id(testChangeId).get().status).isEqualTo(ChangeStatus.MERGED);
  }

  private void postCheckResult(CheckerUuid checkerUuid, CheckState checkState) {
    CheckKey checkKey = CheckKey.create(project, testPatchSetId, checkerUuid);
    checkOperations.newCheck(checkKey).setState(checkState).upsert();
  }
}
