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

import com.google.auto.value.AutoValue;
import com.google.gerrit.acceptance.testsuite.ThrowingConsumer;
import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.api.CheckState;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@AutoValue
public abstract class TestCheckUpdate {
  public abstract CheckKey key();

  public abstract Optional<CheckState> state();

  public abstract Optional<String> message();

  public abstract Optional<String> url();

  public abstract Optional<Timestamp> started();

  public abstract Optional<Timestamp> finished();

  abstract ThrowingConsumer<TestCheckUpdate> checkUpdater();

  public abstract Builder toBuilder();

  public static Builder builder(CheckKey key) {
    return new AutoValue_TestCheckUpdate.Builder().key(key);
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder key(CheckKey key);

    public abstract Builder state(CheckState state);

    public abstract Builder message(String message);

    public Builder clearMessage() {
      return message("");
    }

    public abstract Builder url(String url);

    public Builder clearUrl() {
      return url("");
    }

    public abstract Builder started(Timestamp started);

    public abstract Builder finished(Timestamp finished);

    public Builder clearStarted() {
      return started(Timestamp.from(Instant.EPOCH));
    }

    public Builder clearFinished() {
      return finished(Timestamp.from(Instant.EPOCH));
    }

    abstract Builder checkUpdater(ThrowingConsumer<TestCheckUpdate> checkUpdate);

    abstract TestCheckUpdate autoBuild();

    /** Executes the check update as specified. */
    public void upsert() {
      TestCheckUpdate checkUpdate = autoBuild();
      checkUpdate.checkUpdater().acceptAndThrowSilently(checkUpdate);
    }
  }
}
