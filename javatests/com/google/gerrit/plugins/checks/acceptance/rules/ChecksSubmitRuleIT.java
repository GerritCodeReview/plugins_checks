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

import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.acceptance.AbstractCheckersTest;
import com.google.gerrit.plugins.checks.api.CheckState;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import org.junit.Before;
import org.junit.Test;

/**
 * TODO(xchangcheng): add more tests after figuring out the expecting behavior of {@code
 * CombinedCheckState}.
 */
public class ChecksSubmitRuleIT extends AbstractCheckersTest {

  private String testChangeId;
  private PatchSet.Id testPatchSetId;

  @Before
  public void setUp() throws Exception {
    PushOneCommit.Result result = createChange();
    testPatchSetId = result.getPatchSetId();
    testChangeId = result.getChangeId();

    // Approves "Code-Review" label so that the change only needs to meet the submit requirements
    // about checks.
    approve(testChangeId);
  }

  @Test
  public void nonApplicableCheckerNotBlockingSubmit() throws Exception {
    CheckerUuid checkerUuid = newRequiredChecker().create();
    postCheckResult(checkerUuid, CheckState.FAILED);
    // Updates the checker so that it isn't applicable to the change any more.
    Project.NameKey otherRepo = new Project.NameKey("All-Projects");
    checkerOperations.checker(checkerUuid).forUpdate().repository(otherRepo).update();

    ChangeInfo changeInfo = gApi.changes().id(testChangeId).get();

    assertThat(changeInfo.submittable).isTrue();
  }

  @Test
  public void disabledCheckerDoesNotBlockingSubmit() throws Exception {
    CheckerUuid checkerUuid = newRequiredChecker().create();
    postCheckResult(checkerUuid, CheckState.FAILED);
    checkerOperations.checker(checkerUuid).forUpdate().disable().update();

    ChangeInfo changeInfo = gApi.changes().id(testChangeId).get();

    assertThat(changeInfo.submittable).isTrue();
  }

  // @Test
  // public void enabledCheckerNotBlockingSubmitIfNotRequired() throws Exception {
  //   CheckerUuid checkerUuid = newRequiredChecker().create();
  //   postCheckResult(checkerUuid, CheckState.FAILED);
  //   checkerOperations
  //       .checker(checkerUuid)
  //       .forUpdate()
  //       .blockingConditions(ImmutableSortedSet.of())
  //       .update();
  //
  //   ChangeInfo changeInfo = gApi.changes().id(testChangeId).get();
  //
  //   assertThat(changeInfo.submittable).isTrue();
  // }

  @Test
  public void enabledCheckerNotBlockingSubmitIfNotInBlockingState() throws Exception {
    CheckerUuid checkerUuid = newRequiredChecker().create();
    postCheckResult(checkerUuid, CheckState.SUCCESSFUL);

    ChangeInfo changeInfo = gApi.changes().id(testChangeId).get();

    assertThat(changeInfo.submittable).isTrue();
  }

  @Test
  public void enabledCheckerBlockingSubmitIfInBlockingState() throws Exception {
    CheckerUuid checkerUuid = newRequiredChecker().create();
    postCheckResult(checkerUuid, CheckState.FAILED);

    ChangeInfo changeInfo = gApi.changes().id(testChangeId).get();

    assertThat(changeInfo.submittable).isFalse();
  }

  @Test
  public void multipleCheckerBlockingSubmit() throws Exception {
    CheckerUuid checkerUuid = newRequiredChecker().create();
    // Two enabled and required checkers. They are blocking if any of them isn't passing.
    CheckerUuid testCheckerUuid2 = newRequiredChecker().create();
    postCheckResult(checkerUuid, CheckState.SUCCESSFUL);
    postCheckResult(testCheckerUuid2, CheckState.FAILED);

    ChangeInfo changeInfo = gApi.changes().id(testChangeId).get();

    assertThat(changeInfo.submittable).isFalse();
  }

  // @Test
  // public void multipleCheckerNotBlockingSubmit() throws Exception {
  //   CheckerUuid checkerUuid = newRequiredChecker().create();
  //   // Two enabled checkers. The one failed doesn't block because it's not required.
  //   CheckerUuid testCheckerUuidNotRequired =
  //       newRequiredChecker().blockingConditions(ImmutableSortedSet.of()).create();
  //   postCheckResult(testCheckerUuidNotRequired, CheckState.FAILED);
  //   postCheckResult(checkerUuid, CheckState.SUCCESSFUL);
  //
  //   ChangeInfo changeInfo = gApi.changes().id(testChangeId).get();
  //
  //   assertThat(changeInfo.submittable).isTrue();
  // }

  private void postCheckResult(CheckerUuid checkerUuid, CheckState checkState) {
    CheckKey checkKey = CheckKey.create(project, testPatchSetId, checkerUuid);
    checkOperations.newCheck(checkKey).setState(checkState).upsert();
  }
}
