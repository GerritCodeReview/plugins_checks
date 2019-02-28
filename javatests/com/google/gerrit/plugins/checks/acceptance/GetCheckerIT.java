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

package com.google.gerrit.plugins.checks.acceptance;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;

import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.json.OutputFormat;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.api.CheckerInfo;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.restapi.config.ListCapabilities;
import com.google.gerrit.server.restapi.config.ListCapabilities.CapabilityInfo;
import com.google.inject.Inject;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;

public class GetCheckerIT extends AbstractCheckersTest {
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private ListCapabilities listCapabilities;

  @Test
  public void getChecker() throws Exception {
    String name = "my-checker";
    CheckerUuid checkerUuid = checkerOperations.newChecker().name(name).create();

    CheckerInfo info = checkersApi.id(checkerUuid).get();
    assertThat(info.uuid).isEqualTo(checkerUuid.toString());
    assertThat(info.name).isEqualTo(name);
    assertThat(info.description).isNull();
    assertThat(info.createdOn).isNotNull();
  }

  @Test
  public void getCheckerNormalizesUuid() throws Exception {
    CheckerUuid checkerUuid = CheckerUuid.parse("test:getCheckerNormalizesUuid%20checker");
    assertThat(checkerUuid.toString()).isEqualTo("test:getCheckerNormalizesUuid%20checker");
    checkerOperations.newChecker().uuid(checkerUuid).create();

    assertThat(tryGetUuid(checkerUuid.toString())).hasValue(checkerUuid.toString());
    assertThat(tryGetUuid("test:getCheckerNormalizesUuid%20checker"))
        .hasValue(checkerUuid.toString());
    assertThat(tryGetUuid("TEST:getCheckerNormalizesUuid%20checker"))
        .hasValue(checkerUuid.toString());
    assertThat(tryGetUuid("test:getCh%65ckerNormalizesUuid%20checker"))
        .hasValue(checkerUuid.toString());

    // Escaping is required.
    assertThat(tryGetUuid("test:getCheckerNormalizesUuid checker")).isEmpty();
  }

  @Test
  public void getCheckerAcceptsSingleEscapedUuidOverHttp() throws Exception {
    CheckerUuid checkerUuid =
        CheckerUuid.parse("test:getCheckerAcceptsSingleEscapedUuidOverHttp%20checker");
    checkerOperations.newChecker().uuid(checkerUuid).create();

    //assertThat(tryGetUuidOverHttp("test:getCheckerAcceptsSingleEscapedUuidOverHttp%20checker"))
    //    .hasValue(checkerUuid.toString());
    assertThat(tryGetUuidOverHttp("test%3agetCheckerAcceptsSingleEscapedUuidOverHttp%20checker"))
        .hasValue(checkerUuid.toString());
    assertThat(tryGetUuidOverHttp("test%3AgetCheckerAcceptsSingleEscapedUuidOverHttp%20checker"))
        .hasValue(checkerUuid.toString());
    assertThat(tryGetUuidOverHttp("test:getCheckerAcceptsSingleEscapedUuidOverHttp%2520checker"))
        .isEmpty();
  }

  @Test
  public void getCheckerWithDescription() throws Exception {
    String name = "my-checker";
    String description = "some description";
    CheckerUuid checkerUuid =
        checkerOperations.newChecker().name(name).description(description).create();

    CheckerInfo info = checkersApi.id(checkerUuid).get();
    assertThat(info.uuid).isEqualTo(checkerUuid.toString());
    assertThat(info.name).isEqualTo(name);
    assertThat(info.description).isEqualTo(description);
    assertThat(info.createdOn).isNotNull();
  }

  @Test
  public void getNonExistingCheckerFails() throws Exception {
    CheckerUuid checkerUuid = CheckerUuid.parse("test:non-existing");

    exception.expect(ResourceNotFoundException.class);
    exception.expectMessage("Not found: " + checkerUuid);
    checkersApi.id(checkerUuid);
  }

  @Test
  public void getCheckerByNameFails() throws Exception {
    String name = "my-checker";
    checkerOperations.newChecker().name(name).create();

    exception.expect(ResourceNotFoundException.class);
    exception.expectMessage("Not found: " + name);
    checkersApi.id(name);
  }

  @Test
  public void getCheckerWithoutAdministrateCheckersCapabilityFails() throws Exception {
    String name = "my-checker";
    CheckerUuid checkerUuid = checkerOperations.newChecker().name(name).create();

    requestScopeOperations.setApiUser(user.getId());

    exception.expect(AuthException.class);
    exception.expectMessage("administrateCheckers for plugin checks not permitted");
    checkersApi.id(checkerUuid);
  }

  @Test
  public void administrateCheckersCapabilityIsAdvertised() throws Exception {
    Map<String, CapabilityInfo> capabilities = listCapabilities.apply(new ConfigResource());
    String capability = "checks-administrateCheckers";
    assertThat(capabilities).containsKey(capability);
    CapabilityInfo info = capabilities.get(capability);
    assertThat(info.id).isEqualTo(capability);
    assertThat(info.name).isEqualTo("Administrate Checkers");
  }

  private Optional<String> tryGetUuid(String checkerUuidInput) throws Exception {
    try {
      return Optional.of(checkersApi.id(checkerUuidInput).get().uuid);
    } catch (ResourceNotFoundException e) {
      return Optional.empty();
    }
  }

  private Optional<String> tryGetUuidOverHttp(String checkerUuidInput) throws Exception {
    RestResponse res = adminRestSession.get("/plugins/checks/checkers/" + checkerUuidInput);
    if (res.getStatusCode() == SC_NOT_FOUND) {
      return Optional.empty();
    }
    res.assertOK();

    CheckerInfo info = OutputFormat.JSON.newGson().fromJson(res.getReader(), CheckerInfo.class);
    return Optional.of(info.uuid);
  }
}
