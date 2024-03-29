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

import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.DuplicateKeyException;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.NotifyInfo;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.plugins.checks.Checks.GetCheckOptions;
import com.google.gerrit.plugins.checks.api.CombinedCheckState;
import com.google.gerrit.plugins.checks.email.CombinedCheckStateUpdatedChangeEmailDecorator;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.UserInitiated;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.gerrit.server.mail.EmailFactories;
import com.google.gerrit.server.mail.send.ChangeEmail;
import com.google.gerrit.server.mail.send.MessageIdGenerator;
import com.google.gerrit.server.mail.send.OutgoingEmail;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;

/**
 * API to update checks.
 *
 * <p>Delegates the persistence of checks to the storage layer (see {@link ChecksStorageUpdate}).
 *
 * <p>This class contains additional business logic for updating checks which is independent of the
 * used storage layer (e.g. sending email notifications).
 */
public class ChecksUpdate {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  interface Factory {
    ChecksUpdate create(IdentifiedUser currentUser);

    ChecksUpdate createWithServerIdent();
  }

  private final ChecksStorageUpdate checksStorageUpdate;
  private final CombinedCheckStateCache combinedCheckStateCache;
  private final EmailFactories emailFactories;
  private final ChangeNotes.Factory notesFactory;
  private final PatchSetUtil psUtil;
  private final Checks checks;
  private final Checkers checkers;
  private final NotifyResolver notifyResolver;
  private final MessageIdGenerator messageIdGenerator;

  private final ChangeIndexer changeIndexer;
  private final Optional<IdentifiedUser> currentUser;

  @AssistedInject
  ChecksUpdate(
      @UserInitiated ChecksStorageUpdate checksStorageUpdate,
      CombinedCheckStateCache combinedCheckStateCache,
      EmailFactories emailFactories,
      ChangeNotes.Factory notesFactory,
      PatchSetUtil psUtil,
      Checks checks,
      Checkers checkers,
      NotifyResolver notifyResolver,
      MessageIdGenerator messageIdGenerator,
      ChangeIndexer changeIndexer,
      @Assisted IdentifiedUser currentUser) {
    this.checksStorageUpdate = checksStorageUpdate;
    this.combinedCheckStateCache = combinedCheckStateCache;
    this.emailFactories = emailFactories;
    this.notesFactory = notesFactory;
    this.psUtil = psUtil;
    this.checks = checks;
    this.checkers = checkers;
    this.notifyResolver = notifyResolver;
    this.messageIdGenerator = messageIdGenerator;
    this.changeIndexer = changeIndexer;
    this.currentUser = Optional.of(currentUser);
  }

  @AssistedInject
  ChecksUpdate(
      @ServerInitiated ChecksStorageUpdate checksStorageUpdate,
      CombinedCheckStateCache combinedCheckStateCache,
      EmailFactories emailFactories,
      ChangeNotes.Factory notesFactory,
      PatchSetUtil psUtil,
      Checks checks,
      Checkers checkers,
      NotifyResolver notifyResolver,
      MessageIdGenerator messageIdGenerator,
      ChangeIndexer changeIndexer) {
    this.checksStorageUpdate = checksStorageUpdate;
    this.combinedCheckStateCache = combinedCheckStateCache;
    this.emailFactories = emailFactories;
    this.notesFactory = notesFactory;
    this.psUtil = psUtil;
    this.checks = checks;
    this.checkers = checkers;
    this.notifyResolver = notifyResolver;
    this.messageIdGenerator = messageIdGenerator;
    this.changeIndexer = changeIndexer;
    this.currentUser = Optional.empty();
  }

  public Check createCheck(
      CheckKey key,
      CheckUpdate checkUpdate,
      @Nullable NotifyHandling notifyHandling,
      @Nullable Map<RecipientType, NotifyInfo> notifyDetails)
      throws DuplicateKeyException, BadRequestException, IOException, ConfigInvalidException {
    CombinedCheckState oldCombinedCheckState =
        combinedCheckStateCache.get(key.repository(), key.patchSet());

    Check check = checksStorageUpdate.createCheck(key, checkUpdate);

    CombinedCheckState newCombinedCheckState =
        combinedCheckStateCache.get(key.repository(), key.patchSet());
    maybeIndexChange(
        oldCombinedCheckState, newCombinedCheckState, key.repository(), key.patchSet().changeId());
    maybeSendEmail(
        notifyHandling, notifyDetails, check, oldCombinedCheckState, newCombinedCheckState);

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

    Check check = checksStorageUpdate.updateCheck(key, checkUpdate);

    CombinedCheckState newCombinedCheckState =
        combinedCheckStateCache.get(key.repository(), key.patchSet());
    maybeIndexChange(
        oldCombinedCheckState, newCombinedCheckState, key.repository(), key.patchSet().changeId());
    maybeSendEmail(
        notifyHandling, notifyDetails, check, oldCombinedCheckState, newCombinedCheckState);

    return check;
  }

  private void maybeIndexChange(
      CombinedCheckState oldState,
      CombinedCheckState newState,
      Project.NameKey project,
      Change.Id changeId) {
    if (oldState != newState) {
      changeIndexer.index(project, changeId);
    }
  }

  private void maybeSendEmail(
      @Nullable NotifyHandling notifyHandling,
      @Nullable Map<RecipientType, NotifyInfo> notifyDetails,
      Check updatedCheck,
      CombinedCheckState oldCombinedCheckState,
      CombinedCheckState newCombinedCheckState)
      throws BadRequestException, IOException, ConfigInvalidException {
    if (oldCombinedCheckState == newCombinedCheckState) {
      // do not send an email if the combined check state was not updated
      return;
    }

    CheckKey checkKey = updatedCheck.key();
    ChangeNotes changeNotes =
        notesFactory.create(checkKey.repository(), checkKey.patchSet().changeId());
    if (!checkKey.patchSet().equals(changeNotes.getCurrentPatchSet().id())) {
      // do not send an email for non-current patch sets
      return;
    }

    notifyHandling =
        notifyHandling != null
            ? notifyHandling
            : newCombinedCheckState == CombinedCheckState.SUCCESSFUL
                    || newCombinedCheckState == CombinedCheckState.NOT_RELEVANT
                ? NotifyHandling.ALL
                : NotifyHandling.OWNER;
    NotifyResolver.Result notify = notifyResolver.resolve(notifyHandling, notifyDetails);

    try {
      CombinedCheckStateUpdatedChangeEmailDecorator checksEmailDecorator =
          new CombinedCheckStateUpdatedChangeEmailDecorator();
      checksEmailDecorator.setCombinedCheckState(oldCombinedCheckState, newCombinedCheckState);
      checksEmailDecorator.setCheck(
          checkers
              .getChecker(checkKey.checkerUuid())
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          String.format(
                              "checker %s of check %s not found",
                              checkKey.checkerUuid(), checkKey))),
          updatedCheck);
      checksEmailDecorator.setChecksByChecker(getAllChecksByChecker(checkKey));
      ChangeEmail changeEmail =
          emailFactories.createChangeEmail(
              checkKey.repository(), checkKey.patchSet().changeId(), checksEmailDecorator);
      PatchSet patchSet = psUtil.get(changeNotes, checkKey.patchSet());
      changeEmail.setPatchSet(patchSet);
      OutgoingEmail outgoingEmail =
          emailFactories.createOutgoingEmail("combinedCheckStateUpdate", changeEmail);
      if (currentUser.isPresent()) {
        outgoingEmail.setFrom(currentUser.get().getAccountId());
      }
      outgoingEmail.setNotify(notify);
      outgoingEmail.setMessageId(
          messageIdGenerator.fromChangeUpdate(checkKey.repository(), checkKey.patchSet()));
      outgoingEmail.send();
    } catch (Exception e) {
      logger.atSevere().withCause(e).log(
          "Cannot email update for change %s", checkKey.patchSet().changeId());
    }
  }

  private ImmutableMap<Checker, Check> getAllChecksByChecker(CheckKey checkKey)
      throws IllegalStateException, IOException, ConfigInvalidException {
    ImmutableMap.Builder<Checker, Check> checksByChecker = ImmutableMap.builder();
    for (Check check :
        checks.getChecks(
            checkKey.repository(), checkKey.patchSet(), GetCheckOptions.withBackfilling())) {
      Checker checker =
          checkers
              .getChecker(check.key().checkerUuid())
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          String.format(
                              "checker %s of check %s not found",
                              checkKey.checkerUuid(), check.key())));
      checksByChecker.put(checker, check);
    }
    return checksByChecker.build();
  }
}
