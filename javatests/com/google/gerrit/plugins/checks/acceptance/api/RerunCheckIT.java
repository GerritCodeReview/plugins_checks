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
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.acceptance.AbstractCheckersTest;
import com.google.gerrit.plugins.checks.api.CheckInfo;
import com.google.gerrit.plugins.checks.api.CheckState;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;

public class RerunCheckIT extends AbstractCheckersTest {
  @Inject private RequestScopeOperations requestScopeOperations;

  private PatchSet.Id patchSetId;
  private CheckKey checkKey;

  @Before
  public void setUp() throws Exception {
    patchSetId = createChange().getPatchSetId();

    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    checkKey = CheckKey.create(project, patchSetId, checkerUuid);
  }

  @Test
  public void rerunNotStartedCheck() throws Exception {
    checkOperations.newCheck(checkKey).upsert();

    CheckInfo info = checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).rerun();
    assertThat(info.state).isEqualTo(CheckState.NOT_STARTED);
  }

  @Test
  public void rerunFinishedCheck() throws Exception {
    checkOperations.newCheck(checkKey).state(CheckState.SUCCESSFUL).upsert();

    CheckInfo info = checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).rerun();
    assertThat(info.state).isEqualTo(CheckState.NOT_STARTED);
  }

  @Test
  public void rerunNotExistingCheckThrowsError() throws Exception {
    assertThrows(
        UnprocessableEntityException.class,
        () -> checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).rerun());
  }

  @Test
  public void cannotUpdateCheckWithoutAdministrateCheckers() throws Exception {
    requestScopeOperations.setApiUser(user.id());
    checkOperations.newCheck(checkKey).state(CheckState.SUCCESSFUL).upsert();

    AuthException thrown =
        assertThrows(
            AuthException.class,
            () -> checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).rerun());
    assertThat(thrown).hasMessageThat().contains("not permitted");
  }

  @Test
  public void cannotUpdateCheckAnonymously() throws Exception {
    requestScopeOperations.setApiUserAnonymous();
    checkOperations.newCheck(checkKey).state(CheckState.SUCCESSFUL).upsert();

    AuthException thrown =
        assertThrows(
            AuthException.class,
            () -> checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).rerun());
    assertThat(thrown).hasMessageThat().contains("Authentication required");
  }
}
