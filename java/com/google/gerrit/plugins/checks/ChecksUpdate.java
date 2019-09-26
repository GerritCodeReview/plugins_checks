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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.exceptions.DuplicateKeyException;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.NotifyInfo;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.plugins.checks.api.CombinedCheckState;
import com.google.gerrit.plugins.checks.email.CombinedCheckStateUpdatedSender;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.notedb.ChangeNotes;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;

public abstract class ChecksUpdate {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  protected final Optional<IdentifiedUser> currentUser;
  protected final CombinedCheckStateCache combinedCheckStateCache;

  private final CombinedCheckStateUpdatedSender.Factory combinedCheckStateUpdatedSenderFactory;
  private final ChangeNotes.Factory notesFactory;
  private final PatchSetUtil psUtil;
  private final NotifyResolver notifyResolver;

  protected ChecksUpdate(
      Optional<IdentifiedUser> currentUser,
      CombinedCheckStateCache combinedCheckStateCache,
      CombinedCheckStateUpdatedSender.Factory combinedCheckStateUpdatedSenderFactory,
      ChangeNotes.Factory notesFactory,
      PatchSetUtil psUtil,
      NotifyResolver notifyResolver) {
    this.currentUser = currentUser;
    this.combinedCheckStateCache = combinedCheckStateCache;
    this.combinedCheckStateUpdatedSenderFactory = combinedCheckStateUpdatedSenderFactory;
    this.notesFactory = notesFactory;
    this.psUtil = psUtil;
    this.notifyResolver = notifyResolver;
  }

  public Check createCheck(
      CheckKey key,
      CheckUpdate checkUpdate,
      @Nullable NotifyHandling notifyHandling,
      @Nullable Map<RecipientType, NotifyInfo> notifyDetails)
      throws DuplicateKeyException, BadRequestException, IOException, ConfigInvalidException {
    CombinedCheckState oldCombinedCheckState =
        combinedCheckStateCache.get(key.repository(), key.patchSet());

    Check check = createCheckImpl(key, checkUpdate);

    CombinedCheckState newCombinedCheckState =
        combinedCheckStateCache.get(key.repository(), key.patchSet());
    if (oldCombinedCheckState != newCombinedCheckState) {
      sendEmail(notifyHandling, notifyDetails, key, newCombinedCheckState);
    }

    return check;
  }

  public Check updateCheck(
      CheckKey key,
      CheckUpdate checkUpdate,
      @Nullable NotifyHandling notifyHandling,
      @Nullable Map<RecipientType, NotifyInfo> notifyDetails)
      throws BadRequestException, IOException, ConfigInvalidException {
    CombinedCheckState oldCombinedCheckState =
        combinedCheckStateCache.get(key.repository(), key.patchSet());

    Check check = updateCheckImpl(key, checkUpdate);

    CombinedCheckState newCombinedCheckState =
        combinedCheckStateCache.get(key.repository(), key.patchSet());
    if (oldCombinedCheckState != newCombinedCheckState) {
      sendEmail(notifyHandling, notifyDetails, key, newCombinedCheckState);
    }

    return check;
  }

  protected abstract Check createCheckImpl(CheckKey key, CheckUpdate checkUpdate)
      throws DuplicateKeyException, IOException;

  protected abstract Check updateCheckImpl(CheckKey key, CheckUpdate checkUpdate)
      throws IOException;

  private void sendEmail(
      @Nullable NotifyHandling notifyHandling,
      @Nullable Map<RecipientType, NotifyInfo> notifyDetails,
      CheckKey checkKey,
      CombinedCheckState combinedCheckState)
      throws BadRequestException, IOException, ConfigInvalidException {
    notifyHandling = notifyHandling != null ? notifyHandling : NotifyHandling.ALL;
    NotifyResolver.Result notify = notifyResolver.resolve(notifyHandling, notifyDetails);

    try {
      CombinedCheckStateUpdatedSender sender =
          combinedCheckStateUpdatedSenderFactory.create(
              checkKey.repository(), checkKey.patchSet().changeId());

      if (currentUser.isPresent()) {
        sender.setFrom(currentUser.get().getAccountId());
      }

      ChangeNotes changeNotes =
          notesFactory.create(checkKey.repository(), checkKey.patchSet().changeId());
      PatchSet patchSet = psUtil.get(changeNotes, checkKey.patchSet());
      sender.setPatchSet(patchSet);

      sender.setCombinedCheckState(combinedCheckState);
      sender.setNotify(notify);
      sender.send();
    } catch (Exception e) {
      logger.atSevere().withCause(e).log(
          "Cannot email update for change %s", checkKey.patchSet().changeId());
    }
  }
}
