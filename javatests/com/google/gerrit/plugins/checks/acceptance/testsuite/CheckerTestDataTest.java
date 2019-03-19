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
import static com.google.common.truth.Truth.assert_;

import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.plugins.checks.CheckerQuery;
import com.google.gerrit.plugins.checks.CheckerUrl;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.acceptance.AbstractCheckersTest;
import org.junit.Test;

public class CheckerTestDataTest extends AbstractCheckersTest {
  @Test
  public void verifyTestUuids() throws Exception {
    assertThat(CheckerUuid.isUuid(CheckerTestData.INVALID_UUID)).isFalse();
  }

  @Test
  public void verifyTestUrls() throws Exception {
    try {
      CheckerUrl.clean(CheckerTestData.INVALID_URL);
      assert_().fail("expected BadRequestException");
    } catch (BadRequestException e) {
      assertThat(e)
          .hasMessageThat()
          .isEqualTo("only http/https URLs supported: " + CheckerTestData.INVALID_URL);
    }
  }

  @Test
  public void verifyTestQueries() throws Exception {
    assertInvalidQuery(
        CheckerTestData.QUERY_WITH_UNSUPPORTED_OPERATOR,
        "Unsupported operator: " + CheckerTestData.UNSUPPORTED_OPERATOR);
    assertInvalidQuery(
        CheckerTestData.INVALID_QUERY,
        "Invalid query: "
            + CheckerTestData.INVALID_QUERY
            + "\nline 1:0 no viable alternative at input ':'");
  }

  private static void assertInvalidQuery(String query, String expectedMessage) {
    try {
      CheckerQuery.clean(query);
      assert_().fail("expected BadRequestException");
    } catch (BadRequestException e) {
      assertThat(e).hasMessageThat().isEqualTo(expectedMessage);
    }
  }
}
