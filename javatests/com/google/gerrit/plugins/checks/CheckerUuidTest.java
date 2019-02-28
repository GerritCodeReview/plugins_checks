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
          "test: something with spaces ",
          "test:3948hv(*$!@*(%&)",
          "test:foo\tbar\t\u1234",
          "t-e-s.34-t--:foo");

  private static final ImmutableSet<String> INVALID_CHECKER_UUIDS =
      ImmutableSet.of(
          "",
          "437ee3",
          "Id852b02b44d3148de21603fecbc817d03d6899fe",
          "foo",
          "437ee373885fbc47b103dc722800448320e8bc61",
          "437ee373885fbc47b103dc722800448320e8bc61-foo",
          "437ee373885fbc47b103dc722800448320e8bc61 foo",
          ":foo",
          "f~oo:bar",
          "1foo:bar",
          "foo:bar\0baz",
          "foo:bar\nbaz");

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
    assertThat(CheckerUuid.parse("TEST:my-checker").scheme()).isEqualTo("test");
    assertThat(CheckerUuid.tryParse("TEST:my-checker").map(CheckerUuid::scheme)).hasValue("test");
  }

  @Test
  public void id() {
    assertThat(CheckerUuid.create("test", "my-checker").id()).isEqualTo("my-checker");
    assertThat(CheckerUuid.create("test", " my-checker ").id()).isEqualTo(" my-checker ");
    assertThat(CheckerUuid.create("test", "MY-CHECKER").id()).isEqualTo("MY-CHECKER");
    assertThat(CheckerUuid.create("test", "foo\tbar\t\u1234!!").id())
        .isEqualTo("foo\tbar\t\u1234!!");
  }

  @Test
  public void sha1() {
    assertThat(CheckerUuid.create("test", "my-checker").sha1())
        .isEqualTo("4ff2f443e66636c68b554b5dbb09c90475ae7147");
  }

  @Test
  public void sort() {
    assertThat(
            ImmutableSortedSet.of(
                CheckerUuid.parse("fo:a"), CheckerUuid.parse("foo:a"), CheckerUuid.parse("fo-o:a")))
        .containsExactly(
            CheckerUuid.parse("fo-o:a"), CheckerUuid.parse("fo:a"), CheckerUuid.parse("foo:a"))
        .inOrder();
  }
}
