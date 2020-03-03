package com.google.gerrit.plugins.checks.acceptance.api;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.acceptance.AbstractCheckersTest;
import com.google.gerrit.plugins.checks.api.CheckOverrideInput;
import com.google.gerrit.plugins.checks.api.CheckState;
import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;

public class OverrideCheckIT extends AbstractCheckersTest {

  @Inject private RequestScopeOperations requestScopeOperations;

  private PatchSet.Id patchSetId;
  private CheckKey checkKey;

  @Before
  public void setUp() throws Exception {
    patchSetId = createChange().getPatchSetId();

    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    checkKey = CheckKey.create(project, patchSetId, checkerUuid);
    checkOperations.newCheck(checkKey).state(CheckState.NOT_STARTED).upsert();
  }

  @Test
  public void overrideMustHaveReason() throws Exception {
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
    /*
    TODO:[paiking] implement this test. Curerntly can't be implemented since the endpoint is not implemented
    yet.
     */
  }

  @Test
  public void cannotOverrideAnonymously() throws Exception {
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
}
