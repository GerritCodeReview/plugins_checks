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
import com.google.gerrit.entities.Account;

import java.sql.Timestamp;

/**
 * An override for a check. Overrides influence the submit impact of a check. Checks which are configured as required
 * for submit can be switched to optional via overrides."
 * */

@AutoValue
public abstract class CheckOverride {

    /** User who performed the override. */
    public abstract Account.Id overrider();

    /** Mandatory reason/justification why this override was done. */
    public abstract String reason();

    /** Timestamp of when this override happened. */
    public abstract Timestamp created();


    public abstract Builder toBuilder();

    public static Builder builder() {
        return new AutoValue_CheckOverride.Builder();
    }


    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder setOverrider(Account.Id overrider);

        public abstract Builder setReason(String reason);

        public abstract Builder setCreated(Timestamp created);

        public abstract CheckOverride build();
    }
}
