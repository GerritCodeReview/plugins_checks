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

package com.google.gerrit.plugins.checks.acceptance.testsuite;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSortedSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.plugins.checks.Checker;
import com.google.gerrit.plugins.checks.CheckerRef;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.Checkers;
import com.google.gerrit.plugins.checks.acceptance.AbstractCheckersTest;
import com.google.gerrit.plugins.checks.api.BlockingCondition;
import com.google.gerrit.plugins.checks.api.CheckerInfo;
import com.google.gerrit.plugins.checks.api.CheckerInput;
import com.google.gerrit.plugins.checks.api.CheckerStatus;
import com.google.gerrit.plugins.checks.db.CheckerConfig;
import com.google.gerrit.plugins.checks.db.CheckersByRepositoryNotes;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.Before;
import org.junit.Test;

public class CheckerOperationsImplTest extends AbstractCheckersTest {
  // Use specific subclass instead of depending on the interface field from the base class.
  @SuppressWarnings("hiding")
  private CheckerOperationsImpl checkerOperations;

  private Checkers checkers;

  @Before
  public void setUp() {
    checkerOperations = plugin.getSysInjector().getInstance(CheckerOperationsImpl.class);
    checkers = plugin.getSysInjector().getInstance(Checkers.class);
  }

  @Test
  public void checkerCanBeCreatedWithoutSpecifyingAnyParameters() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().create();

    CheckerInfo foundChecker = getCheckerFromServer(checkerUuid);
    assertThat(foundChecker.uuid).isEqualTo(checkerUuid.get());
    assertThat(foundChecker.name).isNotNull();
    assertThat(foundChecker.repository).isEqualTo(allProjects.get());
    assertThat(foundChecker.status).isEqualTo(CheckerStatus.ENABLED);
    assertThat(foundChecker.query).isEqualTo("status:open");
    assertThat(foundChecker.blocking).isEmpty();
    assertThat(foundChecker.url).isNull();
    assertThat(foundChecker.description).isNull();
    assertThat(foundChecker.created).isNotNull();
    assertThat(foundChecker.updated).isNotNull();
  }

  @Test
  public void twoCheckersWithoutAnyParametersDoNotClash() throws Exception {
    CheckerUuid checkerUuid1 = checkerOperations.newChecker().create();
    CheckerUuid checkerUuid2 = checkerOperations.newChecker().create();

    Checker checker1 = checkerOperations.checker(checkerUuid1).get();
    Checker checker2 = checkerOperations.checker(checkerUuid2).get();
    assertThat(checker1.getUuid()).isNotEqualTo(checker2.getUuid());
  }

  @Test
  public void checkerCreatedByTestApiCanBeRetrievedViaOfficialApi() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().create();

    CheckerInfo foundChecker = getCheckerFromServer(checkerUuid);
    assertThat(foundChecker.uuid).isEqualTo(checkerUuid.get());
  }

  @Test
  public void specifiedNameIsRespectedForCheckerCreation() throws Exception {
    CheckerUuid checkerUuid =
        checkerOperations.newChecker().name("XYZ-123-this-name-must-be-unique").create();

    CheckerInfo checker = getCheckerFromServer(checkerUuid);
    assertThat(checker.name).isEqualTo("XYZ-123-this-name-must-be-unique");
  }

  @Test
  public void specifiedDescriptionIsRespectedForCheckerCreation() throws Exception {
    CheckerUuid checkerUuid =
        checkerOperations.newChecker().description("A simple checker.").create();

    CheckerInfo checker = getCheckerFromServer(checkerUuid);
    assertThat(checker.description).isEqualTo("A simple checker.");
  }

  @Test
  public void requestingNoDescriptionIsPossibleForCheckerCreation() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().clearDescription().create();

    CheckerInfo checker = getCheckerFromServer(checkerUuid);
    assertThat(checker.description).isNull();
  }

  @Test
  public void disabledStatusIsRespectedForCheckerCreation() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().disable().create();

    CheckerInfo checker = getCheckerFromServer(checkerUuid);
    assertThat(checker.status).isEqualTo(CheckerStatus.DISABLED);
  }

  @Test
  public void enabledStatusIsRespectedForCheckerCreation() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().enable().create();

    CheckerInfo checker = getCheckerFromServer(checkerUuid);
    assertThat(checker.status).isEqualTo(CheckerStatus.ENABLED);
  }

  @Test
  public void specifiedRepositoryIsRespectedForCheckerCreation() throws Exception {
    Project.NameKey otherProject = createProjectOverAPI("other", null, true, null);
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(otherProject).create();

    CheckerInfo checker = getCheckerFromServer(checkerUuid);
    assertThat(checker.repository).isEqualTo(otherProject.get());
  }

  @Test
  public void specifiedUrlIsRespectedForCheckerCreation() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().url("http://foo.bar").create();

    CheckerInfo checker = getCheckerFromServer(checkerUuid);
    assertThat(checker.url).isEqualTo("http://foo.bar");
  }

  @Test
  public void requestingNoUrlIsPossibleForCheckerCreation() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().clearUrl().create();

    CheckerInfo checker = getCheckerFromServer(checkerUuid);
    assertThat(checker.url).isNull();
  }

  @Test
  public void specifiedQueryIsRespectedForCheckerCreation() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().query("message:foo").create();

    CheckerInfo checker = getCheckerFromServer(checkerUuid);
    assertThat(checker.query).isEqualTo("message:foo");
  }

  @Test
  public void requestingNoQueryIsPossibleForCheckerCreation() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().clearQuery().create();

    CheckerInfo checker = getCheckerFromServer(checkerUuid);
    assertThat(checker.query).isNull();
  }

  @Test
  public void requiredCheckerCanBeCreated() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().required().create();

    CheckerInfo checker = getCheckerFromServer(checkerUuid);
    assertThat(checker.blocking).isNotEmpty();
  }

  @Test
  public void optionalCheckerCanBeCreated() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().optional().create();

    CheckerInfo checker = getCheckerFromServer(checkerUuid);
    assertThat(checker.blocking).isEmpty();
  }

  @Test
  public void existingCheckerCanBeCheckedForExistence() throws Exception {
    CheckerUuid checkerUuid = createCheckerInServer(createArbitraryCheckerInput());

    boolean exists = checkerOperations.checker(checkerUuid).exists();

    assertThat(exists).isTrue();
  }

  @Test
  public void notExistingCheckerCanBeCheckedForExistence() throws Exception {
    String notExistingCheckerUuid = "test:not-existing-checker";

    boolean exists = checkerOperations.checker(notExistingCheckerUuid).exists();

    assertThat(exists).isFalse();
  }

  @Test
  public void retrievingCheckerForInvalidUuidFails() throws Exception {
    assertThrows(
        IllegalArgumentException.class,
        () -> checkerOperations.checker(CheckerTestData.INVALID_UUID).get());
  }

  @Test
  public void retrievingNotExistingCheckerFails() throws Exception {
    String notExistingCheckerUuid = "foo:bar";

    assertThrows(
        IllegalStateException.class, () -> checkerOperations.checker(notExistingCheckerUuid).get());
  }

  @Test
  public void checkerNotCreatedByTestApiCanBeRetrieved() throws Exception {
    CheckerInput input = createArbitraryCheckerInput();
    input.uuid = "test:unique-checker-not-created-via-test-API";
    CheckerUuid checkerUuid = createCheckerInServer(input);

    Checker foundChecker = checkerOperations.checker(checkerUuid).get();

    assertThat(foundChecker.getUuid()).isEqualTo(checkerUuid);
    assertThat(foundChecker.getUuid().get()).isEqualTo(input.uuid);
  }

  @Test
  public void uuidOfExistingCheckerCanBeRetrieved() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().create();

    CheckerUuid foundCheckerUuid = checkerOperations.checker(checkerUuid).get().getUuid();

    assertThat(foundCheckerUuid).isEqualTo(checkerUuid);
  }

  @Test
  public void nameOfExistingCheckerCanBeRetrieved() throws Exception {
    CheckerUuid checkerUuid =
        checkerOperations.newChecker().name("ABC-789-this-name-must-be-unique").create();

    String checkerName = checkerOperations.checker(checkerUuid).get().getName();

    assertThat(checkerName).isEqualTo("ABC-789-this-name-must-be-unique");
  }

  @Test
  public void descriptionOfExistingCheckerCanBeRetrieved() throws Exception {
    CheckerUuid checkerUuid =
        checkerOperations
            .newChecker()
            .description("This is a very detailed description of this checker.")
            .create();

    Optional<String> description = checkerOperations.checker(checkerUuid).get().getDescription();

    assertThat(description).hasValue("This is a very detailed description of this checker.");
  }

  @Test
  public void emptyDescriptionOfExistingCheckerCanBeRetrieved() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().clearDescription().create();

    Optional<String> description = checkerOperations.checker(checkerUuid).get().getDescription();

    assertThat(description).isEmpty();
  }

  @Test
  public void createdOfExistingCheckerCanBeRetrieved() throws Exception {
    CheckerInfo checker = checkersApi.create(createArbitraryCheckerInput()).get();

    Timestamp created = checkerOperations.checker(checker.uuid).get().getCreated();

    assertThat(created).isEqualTo(checker.created);
  }

  @Test
  public void updatedOfExistingCheckerCanBeRetrieved() throws Exception {
    CheckerInfo checker = checkersApi.create(createArbitraryCheckerInput()).get();

    Timestamp updated = checkerOperations.checker(checker.uuid).get().getUpdated();

    assertThat(updated).isEqualTo(checker.updated);
  }

  @Test
  public void statusOfExistingCheckerCanBeRetrieved() throws Exception {
    CheckerInfo checker = checkersApi.create(createArbitraryCheckerInput()).get();

    CheckerStatus status = checkerOperations.checker(checker.uuid).get().getStatus();

    assertThat(status).isEqualTo(checker.status);
  }

  @Test
  public void repositoryOfExistingCheckerCanBeRetrieved() throws Exception {
    CheckerInfo checker = checkersApi.create(createArbitraryCheckerInput()).get();

    Project.NameKey repository = checkerOperations.checker(checker.uuid).get().getRepository();

    assertThat(repository.get()).isEqualTo(checker.repository);
  }

  @Test
  public void urlOfExistingCheckerCanBeRetrieved() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().url("http://foo.bar").create();

    Optional<String> url = checkerOperations.checker(checkerUuid).get().getUrl();

    assertThat(url).hasValue("http://foo.bar");
  }

  @Test
  public void emptyUrlOfExistingCheckerCanBeRetrieved() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().clearUrl().create();

    Optional<String> url = checkerOperations.checker(checkerUuid).get().getUrl();

    assertThat(url).isEmpty();
  }

  @Test
  public void queryOfExistingCheckerCanBeRetrieved() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().query("message:foo").create();

    Optional<String> query = checkerOperations.checker(checkerUuid).get().getQuery();

    assertThat(query).hasValue("message:foo");
  }

  @Test
  public void blockingConditionsOfExistingCheckerCanBeRetrieved() throws Exception {
    CheckerUuid checkerUuid =
        checkerOperations
            .newChecker()
            .blockingConditions(ImmutableSortedSet.of(BlockingCondition.STATE_NOT_PASSING))
            .create();

    ImmutableSortedSet<BlockingCondition> blockingConditions =
        checkerOperations.checker(checkerUuid).get().getBlockingConditions();

    assertThat(blockingConditions).containsExactly(BlockingCondition.STATE_NOT_PASSING);
  }

  @Test
  public void requiredStateOfExistingCheckerCanBeRetrieved() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().required().create();

    boolean required = checkerOperations.checker(checkerUuid).get().isRequired();

    assertWithMessage("checker.required()").that(required).isTrue();
  }

  @Test
  public void optionalStateOfExistingCheckerCanBeRetrieved() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().optional().create();

    boolean required = checkerOperations.checker(checkerUuid).get().isRequired();

    assertWithMessage("checker.required()").that(required).isFalse();
  }

  @Test
  public void emptyQueryOfExistingCheckerCanBeRetrieved() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().clearQuery().create();

    Optional<String> query = checkerOperations.checker(checkerUuid).get().getQuery();

    assertThat(query).isEmpty();
  }

  @Test
  public void updateWithoutAnyParametersIsANoop() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().create();
    Checker originalChecker = checkerOperations.checker(checkerUuid).get();

    checkerOperations.checker(checkerUuid).forUpdate().update();

    Checker updatedChecker = checkerOperations.checker(checkerUuid).get();
    assertThat(updatedChecker).isEqualTo(originalChecker);
  }

  @Test
  public void updateWritesToInternalCheckerSystem() throws Exception {
    CheckerUuid checkerUuid =
        checkerOperations.newChecker().description("original description").create();

    checkerOperations.checker(checkerUuid).forUpdate().description("updated description").update();

    String currentDescription = getCheckerFromServer(checkerUuid).description;
    assertThat(currentDescription).isEqualTo("updated description");
  }

  @Test
  public void nameCanBeUpdated() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().name("original name").create();

    checkerOperations.checker(checkerUuid).forUpdate().name("updated name").update();

    String currentName = checkerOperations.checker(checkerUuid).get().getName();
    assertThat(currentName).isEqualTo("updated name");
  }

  @Test
  public void descriptionCanBeUpdated() throws Exception {
    CheckerUuid checkerUuid =
        checkerOperations.newChecker().description("original description").create();

    checkerOperations.checker(checkerUuid).forUpdate().description("updated description").update();

    Optional<String> currentDescription =
        checkerOperations.checker(checkerUuid).get().getDescription();
    assertThat(currentDescription).hasValue("updated description");
  }

  @Test
  public void descriptionCanBeCleared() throws Exception {
    CheckerUuid checkerUuid =
        checkerOperations.newChecker().description("original description").create();

    checkerOperations.checker(checkerUuid).forUpdate().clearDescription().update();

    Optional<String> currentDescription =
        checkerOperations.checker(checkerUuid).get().getDescription();
    assertThat(currentDescription).isEmpty();
  }

  @Test
  public void urlCanBeUpdated() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().url("http://foo.bar").create();

    checkerOperations.checker(checkerUuid).forUpdate().url("http://bar.baz").update();

    Optional<String> currentUrl = checkerOperations.checker(checkerUuid).get().getUrl();
    assertThat(currentUrl).hasValue("http://bar.baz");
  }

  @Test
  public void urlCanBeCleared() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().url("http://foo.bar").create();

    checkerOperations.checker(checkerUuid).forUpdate().clearUrl().update();

    Optional<String> currentUrl = checkerOperations.checker(checkerUuid).get().getUrl();
    assertThat(currentUrl).isEmpty();
  }

  @Test
  public void statusCanBeUpdated() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().create();
    assertThat(checkerOperations.checker(checkerUuid).asInfo().status)
        .isEqualTo(CheckerStatus.ENABLED);

    checkerOperations.checker(checkerUuid).forUpdate().disable().update();
    assertThat(checkerOperations.checker(checkerUuid).asInfo().status)
        .isEqualTo(CheckerStatus.DISABLED);

    checkerOperations.checker(checkerUuid).forUpdate().enable().update();
    assertThat(checkerOperations.checker(checkerUuid).asInfo().status)
        .isEqualTo(CheckerStatus.ENABLED);
  }

  @Test
  public void requiredCheckerCanBeMadeOptional() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().required().create();

    checkerOperations.checker(checkerUuid).forUpdate().optional().update();

    assertWithMessage("checker.required()")
        .that(checkerOperations.checker(checkerUuid).get().isRequired())
        .isFalse();
  }

  @Test
  public void optionalCheckerCanBeMadeRequired() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().optional().create();

    checkerOperations.checker(checkerUuid).forUpdate().required().update();
    assertWithMessage("checker.required()")
        .that(checkerOperations.checker(checkerUuid).get().isRequired())
        .isTrue();
  }

  @Test
  public void queryCanBeUpdated() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().query("f:foo").create();

    checkerOperations.checker(checkerUuid).forUpdate().query("f:bar").update();

    Optional<String> currentQuery = checkerOperations.checker(checkerUuid).get().getQuery();
    assertThat(currentQuery).hasValue("f:bar");
  }

  @Test
  public void repositoryCanBeUpdated() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(allProjects).create();

    Project.NameKey otherProject = createProjectOverAPI("other", null, true, null);
    checkerOperations.checker(checkerUuid).forUpdate().repository(otherProject).update();

    Project.NameKey currentRepository =
        checkerOperations.checker(checkerUuid).get().getRepository();
    assertThat(currentRepository).isEqualTo(otherProject);
  }

  @Test
  public void queryCanBeCleared() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().query("f:foo").create();

    checkerOperations.checker(checkerUuid).forUpdate().clearQuery().update();

    Optional<String> currentQuery = checkerOperations.checker(checkerUuid).get().getQuery();
    assertThat(currentQuery).isEmpty();
  }

  @Test
  public void configCanBeMadeInvalid() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().create();

    checkerOperations.checker(checkerUuid).forInvalidation().nonParseableConfig().invalidate();

    assertThrows(ConfigInvalidException.class, () -> checkers.getChecker(checkerUuid));
  }

  @Test
  public void uuidCanBeMadeInvalid() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().create();

    checkerOperations.checker(checkerUuid).forInvalidation().invalidUuid().invalidate();

    ConfigInvalidException thrown =
        assertThrows(ConfigInvalidException.class, () -> checkers.getChecker(checkerUuid));
    assertThat(thrown).hasMessageThat().contains("value of checker.uuid");
    assertThat(thrown).hasMessageThat().contains("does not match expected checker UUID");
  }

  @Test
  public void blockingConditionsCanBeMadeInvalid() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().create();

    checkerOperations
        .checker(checkerUuid)
        .forInvalidation()
        .invalidBlockingCondition()
        .invalidate();

    ConfigInvalidException thrown =
        assertThrows(ConfigInvalidException.class, () -> checkers.getChecker(checkerUuid));
    assertThat(thrown).hasMessageThat().contains("Invalid value: checker.blocking");
  }

  @Test
  public void statusCanBeMadeInvalid() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().create();

    checkerOperations.checker(checkerUuid).forInvalidation().invalidStatus().invalidate();

    ConfigInvalidException thrown =
        assertThrows(ConfigInvalidException.class, () -> checkers.getChecker(checkerUuid));
    assertThat(thrown).hasMessageThat().contains("Invalid value: checker.status");
  }

  @Test
  public void uuidCanBeUnset() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().create();

    checkerOperations.checker(checkerUuid).forInvalidation().unsetUuid().invalidate();

    ConfigInvalidException thrown =
        assertThrows(ConfigInvalidException.class, () -> checkers.getChecker(checkerUuid));
    assertThat(thrown).hasMessageThat().contains("checker.uuid is not set in config file");
  }

  @Test
  public void nameCanBeUnset() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().create();

    checkerOperations.checker(checkerUuid).forInvalidation().unsetName().invalidate();

    ConfigInvalidException thrown =
        assertThrows(ConfigInvalidException.class, () -> checkers.getChecker(checkerUuid));
    assertThat(thrown).hasMessageThat().contains("checker.name is not set in config file");
  }

  @Test
  public void repositoryCanBeUnset() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().create();

    checkerOperations.checker(checkerUuid).forInvalidation().unsetRepository().invalidate();

    ConfigInvalidException thrown =
        assertThrows(ConfigInvalidException.class, () -> checkers.getChecker(checkerUuid));
    assertThat(thrown).hasMessageThat().contains("checker.repository is not set in config file");
  }

  @Test
  public void statusCanBeUnset() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().create();

    checkerOperations.checker(checkerUuid).forInvalidation().unsetStatus().invalidate();

    ConfigInvalidException thrown =
        assertThrows(ConfigInvalidException.class, () -> checkers.getChecker(checkerUuid));
    assertThat(thrown).hasMessageThat().contains("checker.status is not set in config file");
  }

  @Test
  public void refCanBeDeleted() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().create();

    checkerOperations.checker(checkerUuid).forInvalidation().deleteRef().invalidate();

    try (Repository allProjectsRepo = repoManager.openRepository(allProjects)) {
      assertThat(allProjectsRepo.exactRef(checkerUuid.toRefName())).isNull();
    }
  }

  @Test
  public void getCommit() throws Exception {
    CheckerInfo checker = checkersApi.create(createArbitraryCheckerInput()).get();

    RevCommit commit = checkerOperations.checker(checker.uuid).commit();
    assertThat(commit).isEqualTo(readCheckerCommitSha1(CheckerUuid.parse(checker.uuid)));
  }

  private ObjectId readCheckerCommitSha1(CheckerUuid checkerUuid) throws IOException {
    try (Repository repo = repoManager.openRepository(allProjects)) {
      return repo.exactRef(checkerUuid.toRefName()).getObjectId();
    }
  }

  @Test
  public void getConfigText() throws Exception {
    CheckerInfo checker = checkersApi.create(createArbitraryCheckerInput()).get();

    String configText = checkerOperations.checker(checker.uuid).configText();
    assertThat(configText).isEqualTo(readCheckerConfigFile(CheckerUuid.parse(checker.uuid)));
  }

  private String readCheckerConfigFile(CheckerUuid checkerUuid) throws IOException {
    try (Repository repo = repoManager.openRepository(allProjects);
        RevWalk rw = new RevWalk(repo);
        ObjectReader or = repo.newObjectReader()) {
      Ref checkerRef = repo.exactRef(checkerUuid.toRefName());
      RevCommit commit = rw.parseCommit(checkerRef.getObjectId());
      try (TreeWalk tw =
          TreeWalk.forPath(or, CheckerConfig.CHECKER_CONFIG_FILE, commit.getTree())) {
        return new String(or.open(tw.getObjectId(0), OBJ_BLOB).getBytes(), UTF_8);
      }
    }
  }

  @Test
  public void asInfo() throws Exception {
    CheckerUuid checkerUuid =
        checkerOperations
            .newChecker()
            .name("my-checker")
            .description("A description.")
            .url("http://example.com/my-checker")
            .create();
    Checker checker = checkerOperations.checker(checkerUuid).get();
    CheckerInfo checkerInfo = checkerOperations.checker(checkerUuid).asInfo();
    assertThat(checkerInfo.uuid).isEqualTo(checker.getUuid().get());
    assertThat(checkerInfo.name).isEqualTo(checker.getName());
    assertThat(checkerInfo.description).isEqualTo(checker.getDescription().get());
    assertThat(checkerInfo.url).isEqualTo(checker.getUrl().get());
    assertThat(checkerInfo.created).isEqualTo(checker.getCreated());
    assertThat(checkerInfo.updated).isEqualTo(checker.getUpdated());
  }

  @Test
  public void getCheckersOfRepository() throws Exception {
    CheckerUuid checkerUuid1 = CheckerUuid.parse("test:my-checker1");
    CheckerUuid checkerUuid2 = CheckerUuid.parse("test:my-checker2");

    try (Repository repo = repoManager.openRepository(allProjects);
        TestRepository<Repository> testRepo = new TestRepository<>(repo)) {
      testRepo
          .branch(CheckerRef.REFS_META_CHECKERS)
          .commit()
          .add(
              CheckersByRepositoryNotes.computeRepositorySha1(project).getName(),
              Joiner.on('\n').join(checkerUuid1, checkerUuid2))
          .create();
    }

    assertThat(checkerOperations.checkersOf(project)).containsExactly(checkerUuid1, checkerUuid2);
  }

  @Test
  public void getCheckersOfRepositoryWithoutCheckers() throws Exception {
    assertThat(checkerOperations.checkersOf(project)).isEmpty();
  }

  @Test
  public void getCheckersOfNonExistingRepository() throws Exception {
    assertThat(checkerOperations.checkersOf(Project.nameKey("non-existing"))).isEmpty();
  }

  @Test
  public void getSha1sOfRepositoriesWithCheckers() throws Exception {
    CheckerUuid checkerUuid1 = CheckerUuid.parse("test:my-checker1");
    CheckerUuid checkerUuid2 = CheckerUuid.parse("test:my-checker2");

    try (Repository repo = repoManager.openRepository(allProjects);
        TestRepository<Repository> testRepo = new TestRepository<>(repo)) {
      testRepo
          .branch(CheckerRef.REFS_META_CHECKERS)
          .commit()
          .add(
              CheckersByRepositoryNotes.computeRepositorySha1(project).getName(),
              checkerUuid1.toString())
          .add(
              CheckersByRepositoryNotes.computeRepositorySha1(allProjects).getName(),
              checkerUuid2.toString())
          .create();
    }

    assertThat(checkerOperations.sha1sOfRepositoriesWithCheckers())
        .containsExactly(
            CheckersByRepositoryNotes.computeRepositorySha1(project),
            CheckersByRepositoryNotes.computeRepositorySha1(allProjects));
  }

  private CheckerInput createArbitraryCheckerInput() {
    CheckerInput checkerInput = new CheckerInput();
    checkerInput.uuid = "test:my-checker";
    checkerInput.name = "My Checker";
    checkerInput.repository = allProjects.get();
    return checkerInput;
  }

  private CheckerInfo getCheckerFromServer(CheckerUuid checkerUuid) throws RestApiException {
    return checkersApi.id(checkerUuid).get();
  }

  private CheckerUuid createCheckerInServer(CheckerInput input) throws RestApiException {
    CheckerInfo checker = checkersApi.create(input).get();
    return CheckerUuid.parse(checker.uuid);
  }
}
