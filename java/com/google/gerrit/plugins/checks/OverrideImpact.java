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

import com.google.auto.value.AutoValue;
import java.util.Optional;

/** An entity that describes in what way overrides have impacted a specific check. */
@AutoValue
public abstract class OverrideImpact {

  /**
   * Indication whether the {@link OverridePolicy} considers the check as successfully being
   * overridden by the present overrides.
   */
  public abstract boolean overridden();

  /**
   * A short message providing additional details or explanations. This can be used to describe why
   * existing overrides are not sufficient. Another use could be to call out that a special kind of
   * override happened.
   */
  public abstract Optional<String> message();

  public abstract Builder toBuilder();

  public static Builder builder() {
    return new AutoValue_OverrideImpact.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setOverridden(boolean overridden);

    public abstract Builder setMessage(String message);

    public abstract OverrideImpact build();
  }
}
