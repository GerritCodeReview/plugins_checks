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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.plugins.checks.api.CheckOverride;
import com.google.gerrit.plugins.checks.api.CheckState;
import com.google.gerrit.server.util.time.TimeUtil;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.Set;

@AutoValue
public abstract class CheckUpdate {

  @FunctionalInterface
  public interface OverridesModification {
    /**
     * Applies the modification to the given overrides.
     *
     * @param originalOverrides current overrides
     * @return the desired resulting overrides (not the diff of the overrides!)
     */
    Set<CheckOverride> apply(ImmutableSet<CheckOverride> originalOverrides);
  }

  public abstract Optional<CheckState> state();

  public abstract Optional<String> message();

  public abstract Optional<String> url();

  public abstract Optional<Timestamp> started();

  public abstract Optional<Timestamp> finished();

  public abstract OverridesModification overridesModification();

  public abstract Builder toBuilder();

  public static Builder builder() {
    return new AutoValue_CheckUpdate.Builder().setOverridesModification(in -> in);
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setState(CheckState state);

    public abstract Builder setMessage(String message);

    public abstract Builder setUrl(String url);

    public abstract Builder setStarted(Timestamp started);

    public abstract Builder setFinished(Timestamp finished);

    public abstract Builder setOverridesModification(OverridesModification overridesModification);

    public Builder unsetStarted() {
      return setStarted(TimeUtil.never());
    }

    public Builder unsetFinished() {
      return setFinished(TimeUtil.never());
    }

    public abstract CheckUpdate build();
  }
}
