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
import static com.google.common.truth.Truth.assert_;
import static com.google.gerrit.git.testing.CommitSubject.assertCommit;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.plugins.checks.CheckerQuery;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.acceptance.AbstractCheckersTest;
import com.google.gerrit.plugins.checks.acceptance.testsuite.CheckerOperations.PerCheckerOperations;
import com.google.gerrit.plugins.checks.acceptance.testsuite.CheckerTestData;
import com.google.gerrit.plugins.checks.api.BlockingCondition;
import com.google.gerrit.plugins.checks.api.CheckerInfo;
import com.google.gerrit.plugins.checks.api.CheckerInput;
import com.google.gerrit.plugins.checks.api.CheckerStatus;
import com.google.gerrit.plugins.checks.db.CheckersByRepositoryNotes;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.testing.ConfigSuite;
import com.google.gerrit.testing.TestTimeUtil;
import com.google.inject.Inject;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class CreateCheckerIT extends AbstractCheckersTest {
  private static final int MAX_INDEX_TERMS = 10;

  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private ProjectOperations projectOperations;

  @ConfigSuite.Default
  public static Config defaultConfig() {
    Config cfg = new Config();
    cfg.setInt("index", null, "maxTerms", MAX_INDEX_TERMS);
    return cfg;
  }

  @Before
  public void setTimeForTesting() {
    TestTimeUtil.resetWithClockStep(1, TimeUnit.SECONDS);
    TestTimeUtil.setClock(Timestamp.from(Instant.EPOCH));
  }

  @After
  public void resetTime() {
    TestTimeUtil.useSystemTime();
  }

  @Test
  public void createChecker() throws Exception {
    Project.NameKey repositoryName = projectOperations.newProject().create();

    Timestamp expectedCreationTimestamp = TestTimeUtil.getCurrentTimestamp();
    CheckerInput input = new CheckerInput();
    input.uuid = "test:my-checker";
    input.repository = repositoryName.get();
    CheckerInfo info = checkersApi.create(input).get();
    assertThat(info.uuid).isEqualTo("test:my-checker");
    assertThat(info.name).isNull();
    assertThat(info.description).isNull();
    assertThat(info.url).isNull();
    assertThat(info.repository).isEqualTo(input.repository);
    assertThat(info.status).isEqualTo(CheckerStatus.ENABLED);
    assertThat(info.blocking).isEmpty();
    assertThat(info.query).isEqualTo("status:open");
    assertThat(info.created).isEqualTo(expectedCreationTimestamp);
    assertThat(info.updated).isEqualTo(info.created);

    PerCheckerOperations perCheckerOps = checkerOperations.checker(info.uuid);
    assertCommit(
        perCheckerOps.commit(), "Create checker", info.created, perCheckerOps.get().getRefState());
    assertThat(checkerOperations.sha1sOfRepositoriesWithCheckers())
        .containsExactly(CheckersByRepositoryNotes.computeRepositorySha1(repositoryName));
    assertThat(checkerOperations.checkersOf(repositoryName))
        .containsExactly(CheckerUuid.parse(info.uuid));
  }

  @Test
  public void createCheckerWithDescription() throws Exception {
    CheckerInput input = new CheckerInput();
    input.uuid = "test:my-checker";
    input.description = "some description";
    input.repository = allProjects.get();
    CheckerInfo info = checkersApi.create(input).get();
    assertThat(info.description).isEqualTo(input.description);

    PerCheckerOperations perCheckerOps = checkerOperations.checker(info.uuid);
    assertCommit(
        perCheckerOps.commit(), "Create checker", info.created, perCheckerOps.get().getRefState());
  }

  @Test
  public void createCheckerWithUrl() throws Exception {
    CheckerInput input = new CheckerInput();
    input.uuid = "test:my-checker";
    input.url = "http://example.com/my-checker";
    input.repository = allProjects.get();
    CheckerInfo info = checkersApi.create(input).get();
    assertThat(info.url).isEqualTo(input.url);

    PerCheckerOperations perCheckerOps = checkerOperations.checker(info.uuid);
    assertCommit(
        perCheckerOps.commit(), "Create checker", info.created, perCheckerOps.get().getRefState());
  }

  @Test
  public void createCheckerWithName() throws Exception {
    CheckerInput input = new CheckerInput();
    input.uuid = "test:my-checker";
    input.name = "my-checker";
    input.repository = allProjects.get();
    CheckerInfo info = checkersApi.create(input).get();
    assertThat(info.name).isEqualTo("my-checker");

    PerCheckerOperations perCheckerOps = checkerOperations.checker(info.uuid);
    assertCommit(
        perCheckerOps.commit(), "Create checker", info.created, perCheckerOps.get().getRefState());
  }

  @Test
  public void createCheckerNameIsTrimmed() throws Exception {
    CheckerInput input = new CheckerInput();
    input.uuid = "test:my-checker";
    input.name = " my-checker ";
    input.repository = allProjects.get();
    CheckerInfo info = checkersApi.create(input).get();
    assertThat(info.name).isEqualTo("my-checker");

    PerCheckerOperations perCheckerOps = checkerOperations.checker(info.uuid);
    assertCommit(
        perCheckerOps.commit(), "Create checker", info.created, perCheckerOps.get().getRefState());
  }

  @Test
  public void createCheckerDescriptionIsTrimmed() throws Exception {
    CheckerInput input = new CheckerInput();
    input.uuid = "test:my-checker";
    input.description = " some description ";
    input.repository = allProjects.get();
    CheckerInfo info = checkersApi.create(input).get();
    assertThat(info.description).isEqualTo("some description");

    PerCheckerOperations perCheckerOps = checkerOperations.checker(info.uuid);
    assertCommit(
        perCheckerOps.commit(), "Create checker", info.created, perCheckerOps.get().getRefState());
  }

  @Test
  public void createCheckerUrlIsTrimmed() throws Exception {
    CheckerInput input = new CheckerInput();
    input.uuid = "test:my-checker";
    input.url = " http://example.com/my-checker ";
    input.repository = allProjects.get();
    CheckerInfo info = checkersApi.create(input).get();
    assertThat(info.url).isEqualTo("http://example.com/my-checker");

    PerCheckerOperations perCheckerOps = checkerOperations.checker(info.uuid);
    assertCommit(
        perCheckerOps.commit(), "Create checker", info.created, perCheckerOps.get().getRefState());
  }

  @Test
  public void createCheckerRepositoryIsTrimmed() throws Exception {
    CheckerInput input = new CheckerInput();
    input.uuid = "test:my-checker";
    input.repository = " " + allProjects.get() + " ";
    CheckerInfo info = checkersApi.create(input).get();
    assertThat(info.repository).isEqualTo(allProjects.get());

    PerCheckerOperations perCheckerOps = checkerOperations.checker(info.uuid);
    assertCommit(
        perCheckerOps.commit(), "Create checker", info.created, perCheckerOps.get().getRefState());
  }

  @Test
  public void createCheckerWithInvalidUrlFails() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().name("my-checker").create();

    CheckerInput input = new CheckerInput();
    input.url = CheckerTestData.INVALID_URL;
    exception.expect(BadRequestException.class);
    exception.expectMessage("only http/https URLs supported: " + input.url);
    checkersApi.id(checkerUuid).update(input);
  }

  @Test
  public void createCheckersWithSameName() throws Exception {
    CheckerInput input = new CheckerInput();
    input.uuid = "test:my-checker";
    input.name = "my-checker";
    input.repository = allProjects.get();
    CheckerInfo info1 = checkersApi.create(input).get();
    assertThat(info1.name).isEqualTo(input.name);

    input.uuid = "test:another-checker";
    CheckerInfo info2 = checkersApi.create(input).get();
    assertThat(info2.name).isEqualTo(input.name);

    assertThat(info2.uuid).isNotEqualTo(info1.uuid);
  }

  @Test
  public void createCheckerWithExistingUuidFails() throws Exception {
    CheckerInput input = new CheckerInput();
    input.uuid = "test:my-checker";
    input.repository = allProjects.get();
    checkersApi.create(input).get();

    exception.expect(ResourceConflictException.class);
    exception.expectMessage("Checker test:my-checker already exists");
    checkersApi.create(input);
  }

  @Test
  public void createCheckerWithoutUuidFails() throws Exception {
    CheckerInput input = new CheckerInput();
    input.repository = allProjects.get();

    exception.expect(BadRequestException.class);
    exception.expectMessage("uuid is required");
    checkersApi.create(input);
  }

  @Test
  public void createCheckerWithEmptyUuidFails() throws Exception {
    CheckerInput input = new CheckerInput();
    input.uuid = "";
    input.repository = allProjects.get();

    exception.expect(BadRequestException.class);
    exception.expectMessage("uuid is required");
    checkersApi.create(input);
  }

  @Test
  public void createCheckerWithEmptyUuidAfterTrimFails() throws Exception {
    CheckerInput input = new CheckerInput();
    input.uuid = " ";
    input.repository = allProjects.get();

    exception.expect(BadRequestException.class);
    exception.expectMessage("invalid uuid:  ");
    checkersApi.create(input);
  }

  @Test
  public void createCheckerWithInvalidUuidFails() throws Exception {
    CheckerInput input = new CheckerInput();
    input.uuid = CheckerTestData.INVALID_UUID;
    input.repository = allProjects.get();

    exception.expect(BadRequestException.class);
    exception.expectMessage("invalid uuid: " + input.uuid);
    checkersApi.create(input);
  }

  @Test
  public void createCheckerWithoutRepositoryFails() throws Exception {
    CheckerInput input = new CheckerInput();
    input.uuid = "test:my-checker";

    exception.expect(BadRequestException.class);
    exception.expectMessage("repository is required");
    checkersApi.create(input);
  }

  @Test
  public void createCheckerWithEmptyRepositoryFails() throws Exception {
    CheckerInput input = new CheckerInput();
    input.uuid = "test:my-checker";
    input.repository = "";

    exception.expect(BadRequestException.class);
    exception.expectMessage("repository is required");
    checkersApi.create(input);
  }

  @Test
  public void createCheckerWithEmptyRepositoryAfterTrimFails() throws Exception {
    CheckerInput input = new CheckerInput();
    input.uuid = "test:my-checker";
    input.repository = " ";

    exception.expect(BadRequestException.class);
    exception.expectMessage("repository is required");
    checkersApi.create(input);
  }

  @Test
  public void createCheckerWithNonExistingRepositoryFails() throws Exception {
    CheckerInput input = new CheckerInput();
    input.uuid = "test:my-checker";
    input.repository = "non-existing";

    exception.expect(UnprocessableEntityException.class);
    exception.expectMessage("repository non-existing not found");
    checkersApi.create(input);
  }

  @Test
  public void createDisabledChecker() throws Exception {
    CheckerInput input = new CheckerInput();
    input.uuid = "test:my-checker";
    input.repository = allProjects.get();
    input.status = CheckerStatus.DISABLED;

    CheckerInfo info = checkersApi.create(input).get();
    assertThat(info.status).isEqualTo(CheckerStatus.DISABLED);
  }

  @Test
  public void createCheckerWithBlockingConditions() throws Exception {
    CheckerInput input = new CheckerInput();
    input.uuid = "test:my-checker";
    input.repository = allProjects.get();
    input.blocking = ImmutableSet.of(BlockingCondition.STATE_NOT_PASSING);

    CheckerInfo info = checkersApi.create(input).get();
    assertThat(info.blocking).containsExactly(BlockingCondition.STATE_NOT_PASSING);
  }

  @Test
  public void createCheckerWithQuery() throws Exception {
    CheckerInput input = new CheckerInput();
    input.uuid = "test:my-checker";
    input.repository = allProjects.get();
    input.query = "f:foo";

    CheckerInfo info = checkersApi.create(input).get();
    assertThat(info.query).isEqualTo("f:foo");
  }

  @Test
  public void createCheckerWithEmptyQuery() throws Exception {
    CheckerInput input = new CheckerInput();
    input.uuid = "test:my-checker";
    input.repository = allProjects.get();
    input.query = "";

    CheckerInfo info = checkersApi.create(input).get();
    assertThat(info.query).isNull();
  }

  @Test
  public void createCheckerWithEmptyQueryAfterTrim() throws Exception {
    CheckerInput input = new CheckerInput();
    input.uuid = "test:my-checker";
    input.repository = allProjects.get();
    input.query = " ";

    CheckerInfo info = checkersApi.create(input).get();
    assertThat(info.query).isNull();
  }

  @Test
  public void createCheckerWithUnsupportedOperatorInQueryFails() throws Exception {
    CheckerInput input = new CheckerInput();
    input.uuid = "test:my-checker";
    input.repository = allProjects.get();
    input.query = CheckerTestData.QUERY_WITH_UNSUPPORTED_OPERATOR;

    try {
      checkersApi.create(input).get();
      assert_().fail("expected BadRequestException");
    } catch (BadRequestException e) {
      assertThat(e)
          .hasMessageThat()
          .isEqualTo("Unsupported operator: " + CheckerTestData.UNSUPPORTED_OPERATOR);
    }
  }

  @Test
  public void createCheckerWithInvalidQueryFails() throws Exception {
    CheckerInput input = new CheckerInput();
    input.uuid = "test:my-checker";
    input.repository = allProjects.get();
    input.query = CheckerTestData.INVALID_QUERY;

    try {
      checkersApi.create(input).get();
      assert_().fail("expected BadRequestException");
    } catch (BadRequestException e) {
      assertThat(e).hasMessageThat().contains("Invalid query: " + input.query);
    }
  }

  @Test
  public void createCheckerWithTooLongQueryFails() throws Exception {
    CheckerInput input = new CheckerInput();
    input.uuid = "test:my-checker";
    input.repository = allProjects.get();
    input.query = CheckerTestData.longQueryWithSupportedOperators(MAX_INDEX_TERMS * 2);
    assertThat(CheckerQuery.clean(input.query)).isEqualTo(input.query);
    try {
      checkersApi.create(input).get();
      assert_().fail("expected BadRequestException");
    } catch (BadRequestException e) {
      assertThat(e)
          .hasMessageThat()
          .isEqualTo(
              "change query of checker "
                  + input.uuid
                  + " is invalid: "
                  + input.query
                  + " (too many terms in query)");
    }
  }

  @Test
  public void createMultipleCheckers() throws Exception {
    Project.NameKey repositoryName1 = projectOperations.newProject().create();
    Project.NameKey repositoryName2 = projectOperations.newProject().create();

    CheckerUuid checkerUuid1 = checkerOperations.newChecker().repository(repositoryName1).create();
    CheckerUuid checkerUuid2 = checkerOperations.newChecker().repository(repositoryName1).create();
    CheckerUuid checkerUuid3 = checkerOperations.newChecker().repository(repositoryName1).create();
    CheckerUuid checkerUuid4 = checkerOperations.newChecker().repository(repositoryName2).create();
    CheckerUuid checkerUuid5 = checkerOperations.newChecker().repository(repositoryName2).create();

    assertThat(checkerOperations.sha1sOfRepositoriesWithCheckers())
        .containsExactly(
            CheckersByRepositoryNotes.computeRepositorySha1(repositoryName1),
            CheckersByRepositoryNotes.computeRepositorySha1(repositoryName2));
    assertThat(checkerOperations.checkersOf(repositoryName1))
        .containsExactly(checkerUuid1, checkerUuid2, checkerUuid3);
    assertThat(checkerOperations.checkersOf(repositoryName2))
        .containsExactly(checkerUuid4, checkerUuid5);
  }

  @Test
  public void createCheckerWithoutAdministrateCheckersCapabilityFails() throws Exception {
    requestScopeOperations.setApiUser(user.id());

    CheckerInput input = new CheckerInput();
    input.uuid = "test:my-checker";
    input.repository = allProjects.get();

    exception.expect(AuthException.class);
    exception.expectMessage("administrateCheckers for plugin checks not permitted");
    checkersApi.create(input);
  }

  @Test
  public void createCheckerAnonymouslyFails() throws Exception {
    requestScopeOperations.setApiUserAnonymous();

    CheckerInput input = new CheckerInput();
    input.uuid = "test:my-checker";
    input.repository = allProjects.get();

    exception.expect(AuthException.class);
    exception.expectMessage("Authentication required");
    checkersApi.create(input);
  }
}
