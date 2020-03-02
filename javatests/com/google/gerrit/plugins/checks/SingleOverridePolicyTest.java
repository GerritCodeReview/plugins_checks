// Copyright (C) 2020 The Android Open Source Project
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

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.Account;
import com.google.gerrit.server.util.time.TimeUtil;
import java.util.Optional;
import org.junit.Test;

public class SingleOverridePolicyTest {

  @Test
  public void emptyOverridesIsNotOverridden() {
    SingleOverridePolicy singleOverridePolicy = new SingleOverridePolicy();
    OverrideImpact overrideImpact = singleOverridePolicy.computeImpact(ImmutableSet.of());
    assertThat(overrideImpact.message()).isEqualTo(Optional.empty());
    assertThat(overrideImpact.overridden()).isFalse();
  }

  @Test
  public void nonEmptyOverridesIsOverridden() {
    SingleOverridePolicy singleOverridePolicy = new SingleOverridePolicy();
    OverrideImpact overrideImpact =
        singleOverridePolicy.computeImpact(
            ImmutableSet.of(
                CheckOverride.builder()
                    .setOverrider(Account.id(1))
                    .setReason("reason")
                    .setCreated(TimeUtil.nowTs())
                    .build()));
    assertThat(overrideImpact.message()).isEqualTo(Optional.empty());
    assertThat(overrideImpact.overridden()).isTrue();
  }
}
