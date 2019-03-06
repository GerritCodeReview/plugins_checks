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

import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.acceptance.AbstractCheckersTest;
import com.google.gerrit.plugins.checks.acceptance.testsuite.CheckOperations.PerCheckOperations;
import com.google.gerrit.plugins.checks.api.CheckInfo;
import com.google.gerrit.plugins.checks.api.CheckInput;
import com.google.gerrit.plugins.checks.api.CheckState;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.testing.TestTimeUtil;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class CreateCheckIT extends AbstractCheckersTest {
  private PatchSet.Id patchSetId;
  private RevId revId;

  @Before
  public void setTimeForTesting() throws Exception {
    TestTimeUtil.resetWithClockStep(1, TimeUnit.SECONDS);
    TestTimeUtil.setClock(Timestamp.from(Instant.EPOCH));

    patchSetId = createChange().getPatchSetId();
    revId = new RevId(gApi.changes().id(patchSetId.changeId.get()).current().commit(false).commit);
  }

  @After
  public void resetTime() {
    TestTimeUtil.useSystemTime();
  }

  @Test
  public void createCheck() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().create();

    CheckInput input = new CheckInput();
    input.checkerUuid = checkerUuid.toString();
    input.state = CheckState.RUNNING;

    CheckInfo info = checksApiFactory.revision(patchSetId).create(input).get();
    assertThat(info.checkerUuid).isEqualTo(checkerUuid.toString());
    assertThat(info.state).isEqualTo(CheckState.RUNNING);
    assertThat(info.started).isNull();
    assertThat(info.finished).isNull();
    assertThat(info.created).isNotNull();
    assertThat(info.updated).isNotNull();

    CheckKey key = CheckKey.create(project, patchSetId, checkerUuid.toString());
    PerCheckOperations perCheckOps = checkOperations.check(key);

    // TODO(gerrit-team) Add a Truth subject for the notes map
    Map<RevId, String> notes = perCheckOps.notesAsText();
    assertThat(notes).containsExactly(revId, noteDbContent(checkerUuid.toString()));
  }

  // TODO(gerrit-team) More tests, especially for multiple checkers and PS and how commits behave

  private String noteDbContent(String uuid) {
    return ""
        + "{\n"
        + "  \"checks\": {\n"
        + "    \""
        + uuid
        + "\": {\n"
        + "      \"state\": \"RUNNING\",\n"
        + "      \"created\": \"1970-01-01T00:00:25Z\",\n"
        + "      \"updated\": \"1970-01-01T00:00:25Z\"\n"
        + "    }\n"
        + "  }\n"
        + "}";
  }
}
