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

@AutoValue
public abstract class TestCheckInvalidation {
  public abstract boolean invalidState();

  public abstract boolean invalidCreated();

  public abstract boolean invalidUpdated();

  public abstract boolean invalidStarted();

  public abstract boolean invalidFinished();

  public abstract boolean unsetState();

  public abstract boolean unsetCreated();

  public abstract boolean unsetUpdated();

  abstract ThrowingConsumer<TestCheckInvalidation> checkInvalidator();

  public static Builder builder(ThrowingConsumer<TestCheckInvalidation> checkInvalidator) {
    return new AutoValue_TestCheckInvalidation.Builder()
        .checkInvalidator(checkInvalidator)
        .invalidState(false)
        .invalidCreated(false)
        .invalidUpdated(false)
        .invalidStarted(false)
        .invalidFinished(false)
        .unsetState(false)
        .unsetCreated(false)
        .unsetUpdated(false);
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public Builder invalidState() {
      return invalidState(true);
    }

    abstract Builder invalidState(boolean invalidState);

    public Builder invalidCreated() {
      return invalidCreated(true);
    }

    abstract Builder invalidCreated(boolean invalidCreated);

    public Builder invalidUpdated() {
      return invalidUpdated(true);
    }

    abstract Builder invalidUpdated(boolean invalidUpdated);

    public Builder invalidStarted() {
      return invalidStarted(true);
    }

    abstract Builder invalidStarted(boolean invalidStarted);

    public Builder invalidFinished() {
      return invalidFinished(true);
    }

    abstract Builder invalidFinished(boolean invalidFinished);

    public Builder unsetState() {
      return unsetState(true);
    }

    abstract Builder unsetState(boolean unsetState);

    public Builder unsetCreated() {
      return unsetCreated(true);
    }

    abstract Builder unsetCreated(boolean unsetCreated);

    public Builder unsetUpdated() {
      return unsetUpdated(true);
    }

    abstract Builder unsetUpdated(boolean unsetUpdated);

    abstract Builder checkInvalidator(ThrowingConsumer<TestCheckInvalidation> checkInvalidator);

    abstract TestCheckInvalidation autoBuild();

    /** Executes the checker invalidation as specified. */
    public void invalidate() {
      TestCheckInvalidation checkInvalidation = autoBuild();
      checkInvalidation.checkInvalidator().acceptAndThrowSilently(checkInvalidation);
    }
  }
}
