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

package com.google.gerrit.plugins.checks.testing;

import static com.google.common.truth.Truth.assertAbout;

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.MapSubject;
import com.google.common.truth.Subject;
import com.google.common.truth.Truth;
import com.google.gerrit.plugins.checks.api.CheckablePatchSetInfo;
import com.google.gerrit.plugins.checks.api.PendingCheckInfo;
import com.google.gerrit.plugins.checks.api.PendingChecksInfo;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import java.util.Map;

public class PendingChecksInfoSubject extends Subject<PendingChecksInfoSubject, PendingChecksInfo> {
  public static PendingChecksInfoSubject assertThat(PendingChecksInfo pendingChecksInfo) {
    return assertAbout(PendingChecksInfoSubject::new).that(pendingChecksInfo);
  }

  private PendingChecksInfoSubject(FailureMetadata metadata, PendingChecksInfo actual) {
    super(metadata, actual);
  }

  public void hasProject(Project.NameKey expectedProject) {
    Truth.assertThat(patchSet().project).named("project").isEqualTo(expectedProject.get());
  }

  public void hasPatchSet(PatchSet.Id expectedPatchSetId) {
    CheckablePatchSetInfo patchSet = patchSet();
    Truth.assertThat(patchSet.changeNumber)
        .named("change number")
        .isEqualTo(expectedPatchSetId.getParentKey().get());
    Truth.assertThat(patchSet.patchSetId).named("patch set ID").isEqualTo(expectedPatchSetId.get());
  }

  public MapSubject hasPendingChecksMapThat() {
    return Truth.assertThat(pendingChecks()).named("pending checks");
  }

  private CheckablePatchSetInfo patchSet() {
    isNotNull();
    CheckablePatchSetInfo patchSet = actual().patchSet;
    Truth.assertThat(patchSet).named("patch set").isNotNull();
    return patchSet;
  }

  private Map<String, PendingCheckInfo> pendingChecks() {
    isNotNull();
    Map<String, PendingCheckInfo> pendingChecks = actual().pendingChecks;
    Truth.assertThat(pendingChecks).named("pending checks").isNotNull();
    return pendingChecks;
  }
}
