package com.google.gerrit.plugins.checks.acceptance.api;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.ListChecksOption;
import com.google.gerrit.plugins.checks.acceptance.AbstractCheckersTest;
import com.google.gerrit.plugins.checks.api.CheckInfo;
import com.google.gerrit.plugins.checks.api.CheckOverrideInfo;
import com.google.gerrit.plugins.checks.api.CheckOverrideInput;
import com.google.gerrit.plugins.checks.api.CheckState;
import com.google.gerrit.plugins.checks.api.CheckSubmitImpactInfo;
import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;

public class OverrideCheckIT extends AbstractCheckersTest {

  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private ProjectOperations projectOperations;

  private PatchSet.Id patchSetId;
  private CheckKey checkKey;

  @Before
  public void setUp() throws Exception {
    patchSetId = createChange().getPatchSetId();

    CheckerUuid checkerUuid =
        checkerOperations.newChecker().repository(project).required().create();
    checkKey = CheckKey.create(project, patchSetId, checkerUuid);
  }

  @Test
  public void overrideMustHaveReason() throws Exception {
    checkOperations.newCheck(checkKey).state(CheckState.NOT_STARTED).upsert();
    CheckOverrideInput checkOverrideInput = new CheckOverrideInput();
    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () ->
                checksApiFactory
                    .revision(patchSetId)
                    .id(checkKey.checkerUuid())
                    .override(checkOverrideInput));
    assertThat(thrown).hasMessageThat().contains("Override must have reason");
  }

  @Test
  public void reasonLimitedTo300Characters() throws Exception {
    checkOperations.newCheck(checkKey).state(CheckState.NOT_STARTED).upsert();
    CheckOverrideInput checkOverrideInput = new CheckOverrideInput();
    checkOverrideInput.reason = "";
    for (int i = 0; i < 301; i++) {
      checkOverrideInput.reason += "a";
    }
    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () ->
                checksApiFactory
                    .revision(patchSetId)
                    .id(checkKey.checkerUuid())
                    .override(checkOverrideInput));
    assertThat(thrown)
        .hasMessageThat()
        .contains("Reason for override is too long. It must be " + "up to 300 characters.");
  }

  @Test
  public void cantOverrideTwice() throws Exception {
    checkOperations.newCheck(checkKey).state(CheckState.NOT_STARTED).upsert();
    CheckOverrideInput checkOverrideInput = new CheckOverrideInput();
    checkOverrideInput.reason = "testing";
    checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).override(checkOverrideInput);
    ResourceConflictException thrown =
        assertThrows(
            ResourceConflictException.class,
            () ->
                checksApiFactory
                    .revision(patchSetId)
                    .id(checkKey.checkerUuid())
                    .override(checkOverrideInput));
    assertThat(thrown)
        .hasMessageThat()
        .contains("The same user can't override the same check twice");
  }

  @Test
  public void cannotOverrideAnonymously() throws Exception {
    checkOperations.newCheck(checkKey).state(CheckState.NOT_STARTED).upsert();

    requestScopeOperations.setApiUserAnonymous();
    CheckOverrideInput checkOverrideInput = new CheckOverrideInput();
    checkOverrideInput.reason = "testing";
    AuthException thrown =
        assertThrows(
            AuthException.class,
            () ->
                checksApiFactory
                    .revision(patchSetId)
                    .id(checkKey.checkerUuid())
                    .override(checkOverrideInput));
    assertThat(thrown).hasMessageThat().contains("Authentication required");
  }

  @Test
  public void cantOverrideWithoutPermissions() throws Exception {
    checkOperations.newCheck(checkKey).state(CheckState.NOT_STARTED).upsert();
    requestScopeOperations.setApiUser(user.id());
    CheckOverrideInput checkOverrideInput = new CheckOverrideInput();
    checkOverrideInput.reason = "testing";

    AuthException thrown =
        assertThrows(
            AuthException.class,
            () ->
                checksApiFactory
                    .revision(patchSetId)
                    .id(checkKey.checkerUuid())
                    .override(checkOverrideInput));
    assertThat(thrown).hasMessageThat().contains("not permitted");
  }

  @Test
  public void overrideExistingCheck() throws Exception {
    checkOperations.newCheck(checkKey).state(CheckState.NOT_STARTED).upsert();
    CheckInfo checkInfo =
        checksApiFactory
            .revision(patchSetId)
            .id(checkKey.checkerUuid())
            .get(ListChecksOption.SUBMIT_IMPACT);
    assertThat(checkInfo.submitImpact.required).isTrue();
    assertThat(checkInfo.submitImpact.overrides).isNull();

    CheckOverrideInput checkOverrideInput = new CheckOverrideInput();
    checkOverrideInput.reason = "testing";
    CheckInfo info =
        checksApiFactory
            .revision(patchSetId)
            .id(checkKey.checkerUuid())
            .override(checkOverrideInput);
    CheckSubmitImpactInfo checkSubmitImpactInfo = info.submitImpact;

    assertThat(checkSubmitImpactInfo).isNotNull();
    assertThat(checkSubmitImpactInfo.overrides).hasSize(1);

    CheckOverrideInfo checkOverride = checkSubmitImpactInfo.overrides.iterator().next();
    assertThat(checkOverride.overrider._accountId).isEqualTo(admin.id().get());
    assertThat(checkOverride.overriddenOn).isNotNull();
    assertThat(checkOverride.reason).isEqualTo(checkOverrideInput.reason);

    assertThat(checkSubmitImpactInfo.required).isFalse();
    assertThat(checkSubmitImpactInfo.message).isNull();
  }

  @Test
  public void overrideCheckNotExisting() throws Exception {
    checkOperations.newCheck(checkKey).state(CheckState.NOT_STARTED).upsert();

    CheckerUuid notPresentUuid =
        checkerOperations.newChecker().repository(project).required().create();
    CheckKey notPresentKey = CheckKey.create(project, patchSetId, notPresentUuid);

    CheckOverrideInput checkOverrideInput = new CheckOverrideInput();
    checkOverrideInput.reason = "testing";
    CheckInfo info =
        checksApiFactory
            .revision(patchSetId)
            .id(notPresentKey.checkerUuid())
            .override(checkOverrideInput);
    CheckSubmitImpactInfo checkSubmitImpactInfo = info.submitImpact;

    assertThat(checkSubmitImpactInfo).isNotNull();
    assertThat(checkSubmitImpactInfo.overrides).hasSize(1);

    CheckOverrideInfo checkOverride = checkSubmitImpactInfo.overrides.iterator().next();
    assertThat(checkOverride.overrider._accountId).isEqualTo(admin.id().get());
    assertThat(checkOverride.overriddenOn).isNotNull();
    assertThat(checkOverride.reason).isEqualTo(checkOverrideInput.reason);

    assertThat(checkSubmitImpactInfo.required).isFalse();
    assertThat(checkSubmitImpactInfo.message).isNull();
  }

  @Test
  public void multipleOverridesAllowed() throws Exception {
    checkOperations.newCheck(checkKey).state(CheckState.NOT_STARTED).upsert();
    CheckOverrideInput checkOverrideInput = new CheckOverrideInput();
    checkOverrideInput.reason = "testing";
    Account.Id user = admin.id();

    for (int i = 0; i <= 1; i++) {
      CheckInfo info =
          checksApiFactory
              .revision(patchSetId)
              .id(checkKey.checkerUuid())
              .override(checkOverrideInput);
      CheckSubmitImpactInfo checkSubmitImpactInfo = info.submitImpact;

      assertThat(checkSubmitImpactInfo).isNotNull();
      assertThat(checkSubmitImpactInfo.overrides).hasSize(i + 1);

      CheckOverrideInfo checkOverride = checkSubmitImpactInfo.overrides.get(i);
      assertThat(checkOverride.overrider._accountId).isEqualTo(user.get());
      assertThat(checkOverride.overriddenOn).isNotNull();
      assertThat(checkOverride.reason).isEqualTo(checkOverrideInput.reason);

      assertThat(checkSubmitImpactInfo.required).isFalse();
      assertThat(checkSubmitImpactInfo.message).isNull();
      user = accountCreator.admin2().id();
      requestScopeOperations.setApiUser(user);
    }
  }

  @Test
  public void overrideExistingCheckWithCheckerNotAppliedToChange() throws Exception {
    Project.NameKey otherProject = projectOperations.newProject().create();
    checkerOperations.checker(checkKey.checkerUuid()).forUpdate().repository(otherProject).update();
    checkOperations.newCheck(checkKey).state(CheckState.NOT_STARTED).upsert();

    CheckOverrideInput checkOverrideInput = new CheckOverrideInput();
    checkOverrideInput.reason = "testing";

    CheckInfo info =
        checksApiFactory
            .revision(patchSetId)
            .id(checkKey.checkerUuid())
            .override(checkOverrideInput);
    assertThat(info.submitImpact.overrides).hasSize(1);
  }

  @Test
  public void overrideNonExistingCheckWithCheckerNotAppliedToChange() throws Exception {
    Project.NameKey otherProject = projectOperations.newProject().create();
    checkerOperations.checker(checkKey.checkerUuid()).forUpdate().repository(otherProject).update();

    CheckOverrideInput checkOverrideInput = new CheckOverrideInput();
    checkOverrideInput.reason = "testing";

    assertThrows(
        ResourceNotFoundException.class,
        () ->
            checksApiFactory
                .revision(patchSetId)
                .id(checkKey.checkerUuid())
                .override(checkOverrideInput));
    assertThat(checkOperations.check(checkKey).exists()).isFalse();
  }
}
