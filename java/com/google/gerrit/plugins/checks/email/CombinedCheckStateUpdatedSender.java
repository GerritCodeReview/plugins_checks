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

package com.google.gerrit.plugins.checks.email;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import com.google.gerrit.exceptions.EmailException;
import com.google.gerrit.plugins.checks.Check;
import com.google.gerrit.plugins.checks.Checker;
import com.google.gerrit.plugins.checks.api.CombinedCheckState;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.account.ProjectWatches.NotifyType;
import com.google.gerrit.server.mail.send.ChangeEmail;
import com.google.gerrit.server.mail.send.EmailArguments;
import com.google.gerrit.server.mail.send.ReplyToChangeSender;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.HashMap;
import java.util.Map;

/** Send notice about an update of the combined check state of a change. */
public class CombinedCheckStateUpdatedSender extends ReplyToChangeSender {
  public interface Factory extends ReplyToChangeSender.Factory<CombinedCheckStateUpdatedSender> {
    @Override
    CombinedCheckStateUpdatedSender create(Project.NameKey project, Change.Id changeId);
  }

  private CombinedCheckState oldCombinedCheckState;
  private CombinedCheckState newCombinedCheckState;
  private Checker checker;
  private Check check;

  @Inject
  public CombinedCheckStateUpdatedSender(
      EmailArguments args, @Assisted Project.NameKey project, @Assisted Change.Id changeId) {
    super(args, "combinedCheckStateUpdate", ChangeEmail.newChangeData(args, project, changeId));
  }

  @Override
  protected void init() throws EmailException {
    super.init();

    ccAllApprovals();
    bccStarredBy();
    includeWatchers(NotifyType.ALL_COMMENTS);
    removeUsersThatIgnoredTheChange();
  }

  public void setCombinedCheckState(
      CombinedCheckState oldCombinedCheckState, CombinedCheckState newCombinedCheckState) {
    this.oldCombinedCheckState = requireNonNull(oldCombinedCheckState);
    this.newCombinedCheckState = requireNonNull(newCombinedCheckState);
  }

  public void setCheck(Checker checker, Check check) {
    requireNonNull(check, "check is missing");
    requireNonNull(checker, "checker is missing");
    checkState(
        check.key().checkerUuid().equals(checker.getUuid()),
        "checker %s doesn't match check %s",
        checker.getUuid(),
        check.key());

    this.checker = checker;
    this.check = check;
  }

  @Override
  protected void setupSoyContext() {
    super.setupSoyContext();

    if (oldCombinedCheckState != null) {
      soyContext.put("oldCombinedCheckState", oldCombinedCheckState.name());
    }

    if (newCombinedCheckState != null) {
      soyContext.put("newCombinedCheckState", newCombinedCheckState.name());
    }

    if (checker != null) {
      Map<String, String> checkerData = new HashMap<>();
      checkerData.put("uuid", checker.getUuid().get());
      checkerData.put("name", checker.getName());
      checkerData.put("repository", checker.getRepository().get());
      checker
          .getDescription()
          .ifPresent(description -> checkerData.put("description", description));
      checker.getUrl().ifPresent(url -> checkerData.put("url", url));
      soyContext.put("checker", checkerData);
    }

    if (check != null) {
      Map<String, Object> checkData = new HashMap<>();
      checkData.put("checkerUuid", check.key().checkerUuid().get());
      checkData.put("change", check.key().patchSet().changeId().get());
      checkData.put("patchSet", check.key().patchSet().get());
      checkData.put("repository", check.key().repository().get());
      checkData.put("state", check.state().name());
      check.message().ifPresent(message -> checkData.put("message", message));
      check.url().ifPresent(url -> checkData.put("url", url));
      soyContext.put("check", checkData);
    }
  }

  @Override
  protected void formatChange() throws EmailException {
    appendText(textTemplate("CombinedCheckStateUpdated"));
    if (useHtml()) {
      appendHtml(soyHtmlTemplate("CombinedCheckStateUpdatedHtml"));
    }
  }

  @Override
  protected boolean supportsHtml() {
    return true;
  }
}
