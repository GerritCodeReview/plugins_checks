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
import com.google.common.collect.ImmutableList;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwtorm.server.OrmException;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * A database accessor for read calls related to checks.
 *
 * <p>All calls which read checks related details from the database are gathered here. Other classes
 * should always use this class instead of accessing the database directly.
 *
 * <p>This is an interface so that the implementation can be swapped if needed.
 */
public interface Checks {
  /**
   * Returns a {@link List} of {@link Check}s for the given change and patchset.
   *
   * <p>If no checks exist for the given change and patchset, an empty list is returned.
   *
   * @param projectName the name of the project
   * @param patchSetId the ID of the patch set
   * @param options options for getting checks.
   * @return the checks, {@link Optional#empty()} if no checks with the given UUID exists
   * @throws OrmException if the checks couldn't be retrieved from the storage
   * @throws IOException if the checks couldn't be retrieved from the storage
   */
  ImmutableList<Check> getChecks(
      Project.NameKey projectName, PatchSet.Id patchSetId, GetChecksOptions options)
      throws OrmException, IOException;

  /**
   * Returns a {@link Optional} holding a single check. {@code Optional.empty()} if the check does
   * not exist.
   */
  Optional<Check> getCheck(CheckKey checkKey) throws OrmException, IOException;

  @AutoValue
  abstract class GetChecksOptions {

    /** Backfills checks for relevant checkers with default when they don't exist yet. */
    public abstract boolean backfillChecks();

    public abstract Builder toBuilder();

    public static Builder builder() {
      return new AutoValue_Checks_GetChecksOptions.Builder().setBackfillChecks(false);
    }

    public static GetChecksOptions defaults() {
      return builder().build();
    }

    @AutoValue.Builder
    public abstract static class Builder {
      public abstract Builder setBackfillChecks(boolean backfillChecks);

      public abstract GetChecksOptions build();
    }
  }
}
