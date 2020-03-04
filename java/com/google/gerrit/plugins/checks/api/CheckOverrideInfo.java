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

package com.google.gerrit.plugins.checks.api;

import com.google.common.base.MoreObjects;
import com.google.gerrit.extensions.common.AccountInfo;
import java.sql.Timestamp;
import java.util.Objects;

public class CheckOverrideInfo {
  public AccountInfo overrider;
  public String reason;
  public Timestamp overriddenOn;

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof CheckOverrideInfo)) {
      return false;
    }
    CheckOverrideInfo other = (CheckOverrideInfo) o;
    return Objects.equals(other.overrider, overrider)
        && Objects.equals(other.reason, reason)
        && Objects.equals(other.overriddenOn, overriddenOn);
  }

  @Override
  public int hashCode() {
    return Objects.hash(overrider, reason, overriddenOn);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("overrider", overrider)
        .add("reason", reason)
        .add("overriden_on", overriddenOn)
        .toString();
  }
}
