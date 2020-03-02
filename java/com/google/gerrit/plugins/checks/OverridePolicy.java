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

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.extensions.annotations.ExtensionPoint;

@ExtensionPoint
/**
 * Rules regarding how overrides influence the submit impact. Overrides can influence the submit
 * impact of a check in different ways. In this interface, it's possible to determine this
 * behaviour.
 */
public interface OverridePolicy {

  /**
   * Computes whether a check is overridden. This method is used when determining the submit impact
   * of a check. Overrides may cause a check that would otherwise be required for submission, be
   * optional for submission.
   *
   * @param overrides Existing overrides for the check.
   * @return An {@link OverrideImpact} object that gives additional information about the overrides
   *     of a check.
   */
  OverrideImpact computeImpact(ImmutableSet<CheckOverride> overrides);
}
