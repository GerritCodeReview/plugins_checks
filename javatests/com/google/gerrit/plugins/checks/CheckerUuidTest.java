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

package com.google.gerrit.plugins.checks;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.gerrit.testing.GerritBaseTests;
import org.junit.Test;

public class CheckerUuidTest extends GerritBaseTests {
  private static final ImmutableSet<String> VALID_CHECKER_UUIDS =
      ImmutableSet.of(
          "test:my-checker",
          "TEST:MY-checker",
          "test:%20something%20with%20spaces%20",
          "test:3948hv%28%2A%24%21%40%2A%28%25%26%29",
          "test:3948hv%28%2A%24%21%40%2A%28%25%26%29",
          "test:foo%09bar%09%E1%88%B4",
          "foo:bar%00baz",
          "foo:bar%0Abaz",
          "t-e-s.34-t--:foo");

  private static final ImmutableSet<String> INVALID_CHECKER_UUIDS =
      ImmutableSet.of(
          "",
          "437ee3",
          "Id852b02b44d3148de21603fecbc817d03d6899fe",
          "foo",
          "437ee373885fbc47b103dc722800448320e8bc61",
          "437ee373885fbc47b103dc722800448320e8bc61-foo",
          "test: something with spaces ",
          "test:3948hv(*$!@*(%&)",
          "437ee373885fbc47b103dc722800448320e8bc61 foo",
          ":foo",
          "test:foo\tbar\t\u1234",
          "foo:bar\0baz",
          "foo:bar\nbaz",
          "f~oo:bar",
          "1foo:bar",
          "foo:/bar");

  @Test
  public void isUuid() {
    for (String checkerUuid : VALID_CHECKER_UUIDS) {
      assertThat(CheckerUuid.isUuid(checkerUuid)).named(checkerUuid).isTrue();
    }

    assertThat(CheckerUuid.isUuid(null)).isFalse();
    for (String invalidCheckerUuid : INVALID_CHECKER_UUIDS) {
      assertThat(CheckerUuid.isUuid(invalidCheckerUuid)).named(invalidCheckerUuid).isFalse();
    }
  }

  @Test
  public void tryParse() {
    for (String checkerUuid : VALID_CHECKER_UUIDS) {
      assertThat(CheckerUuid.tryParse(checkerUuid)).named(checkerUuid).isPresent();
    }

    assertThat(CheckerUuid.tryParse(null)).isEmpty();
    for (String invalidCheckerUuid : INVALID_CHECKER_UUIDS) {
      assertThat(CheckerUuid.tryParse(invalidCheckerUuid)).named(invalidCheckerUuid).isEmpty();
    }
  }

  @Test
  public void schemeIsConvertedToLowercase() {
    assertThat(CheckerUuid.parse("test:my%20checker").scheme()).isEqualTo("test");
    assertThat(CheckerUuid.parse("TEST:my%20checker").scheme()).isEqualTo("test");
    assertThat(CheckerUuid.parse("TesT:my%20checker").scheme()).isEqualTo("test");
  }

  @Test
  public void uriToString() {
    assertThat(CheckerUuid.parse("test:my%20checker").toString()).isEqualTo("test:my%20checker");
    assertThat(CheckerUuid.parse("test:my%20ch%65cker").toString()).isEqualTo("test:my%20checker");
    assertThat(CheckerUuid.parse("TEST:my%20checker").toString()).isEqualTo("test:my%20checker");
    assertThat(CheckerUuid.parse("TEST:my%20ch%65cker").toString()).isEqualTo("test:my%20checker");
  }

  @Test
  public void sha1() {
    // $ echo -n 'test:my%20checker' | sha1sum
    // 3292dc756557326c9b495d3a880c99f4efc04101  -
    String expectedSha1 = "3292dc756557326c9b495d3a880c99f4efc04101";
    assertThat(CheckerUuid.parse("test:my%20checker").sha1()).isEqualTo(expectedSha1);
    assertThat(CheckerUuid.parse("test:my%20ch%65cker").sha1()).isEqualTo(expectedSha1);
    assertThat(CheckerUuid.parse("TEST:my%20checker").sha1()).isEqualTo(expectedSha1);
    assertThat(CheckerUuid.parse("TEST:my%20ch%65cker").sha1()).isEqualTo(expectedSha1);
  }

  @Test
  public void sort() {
    assertThat(
            ImmutableSortedSet.of(
                CheckerUuid.parse("fo:a"), CheckerUuid.parse("foo:a"), CheckerUuid.parse("fo-o:a")))
        .containsExactly(
            CheckerUuid.parse("fo:a"), CheckerUuid.parse("fo-o:a"), CheckerUuid.parse("foo:a"))
        .inOrder();
  }
}
