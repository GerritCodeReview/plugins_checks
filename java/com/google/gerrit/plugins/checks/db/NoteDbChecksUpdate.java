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

package com.google.gerrit.plugins.checks.db;

import static com.google.gerrit.plugins.checks.CheckerRef.checksRef;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.common.base.Throwables;
import com.google.common.collect.Maps;
import com.google.gerrit.exceptions.DuplicateKeyException;
import com.google.gerrit.git.LockFailureException;
import com.google.gerrit.git.RefUpdateUtil;
import com.google.gerrit.plugins.checks.Check;
import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.CheckUpdate;
import com.google.gerrit.plugins.checks.CheckerRef;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.Checkers;
import com.google.gerrit.plugins.checks.ChecksUpdate;
import com.google.gerrit.plugins.checks.CombinedCheckStateCache;
import com.google.gerrit.plugins.checks.email.CombinedCheckStateUpdatedSender;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNoteUtil;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.update.RetryHelper;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.sql.Timestamp;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevWalk;

public class NoteDbChecksUpdate extends ChecksUpdate {
  interface Factory {
    NoteDbChecksUpdate create(IdentifiedUser currentUser);

    NoteDbChecksUpdate createWithServerIdent();
  }

  private enum Operation {
    CREATE,
    UPDATE
  }

  private final GitRepositoryManager repoManager;
  private final PersonIdent personIdent;
  private final GitReferenceUpdated gitRefUpdated;
  private final RetryHelper retryHelper;
  private final ChangeNoteUtil noteUtil;
  private final Checkers checkers;

  @AssistedInject
  NoteDbChecksUpdate(
      GitRepositoryManager repoManager,
      GitReferenceUpdated gitRefUpdated,
      RetryHelper retryHelper,
      ChangeNoteUtil noteUtil,
      Checkers checkers,
      CombinedCheckStateCache combinedCheckStateCache,
      CombinedCheckStateUpdatedSender.Factory combinedCheckStateUpdatedSenderFactory,
      ChangeNotes.Factory notesFactory,
      PatchSetUtil psUtil,
      NotifyResolver notifyResolver,
      @GerritPersonIdent PersonIdent personIdent) {
    this(
        repoManager,
        gitRefUpdated,
        retryHelper,
        noteUtil,
        checkers,
        combinedCheckStateCache,
        combinedCheckStateUpdatedSenderFactory,
        notesFactory,
        psUtil,
        notifyResolver,
        personIdent,
        Optional.empty());
  }

  @AssistedInject
  NoteDbChecksUpdate(
      GitRepositoryManager repoManager,
      GitReferenceUpdated gitRefUpdated,
      RetryHelper retryHelper,
      ChangeNoteUtil noteUtil,
      Checkers checkers,
      CombinedCheckStateCache combinedCheckStateCache,
      CombinedCheckStateUpdatedSender.Factory combinedCheckStateUpdatedSenderFactory,
      ChangeNotes.Factory notesFactory,
      PatchSetUtil psUtil,
      NotifyResolver notifyResolver,
      @GerritPersonIdent PersonIdent personIdent,
      @Assisted IdentifiedUser currentUser) {
    this(
        repoManager,
        gitRefUpdated,
        retryHelper,
        noteUtil,
        checkers,
        combinedCheckStateCache,
        combinedCheckStateUpdatedSenderFactory,
        notesFactory,
        psUtil,
        notifyResolver,
        personIdent,
        Optional.of(currentUser));
  }

  private NoteDbChecksUpdate(
      GitRepositoryManager repoManager,
      GitReferenceUpdated gitRefUpdated,
      RetryHelper retryHelper,
      ChangeNoteUtil noteUtil,
      Checkers checkers,
      CombinedCheckStateCache combinedCheckStateCache,
      CombinedCheckStateUpdatedSender.Factory combinedCheckStateUpdatedSenderFactory,
      ChangeNotes.Factory notesFactory,
      PatchSetUtil psUtil,
      NotifyResolver notifyResolver,
      @GerritPersonIdent PersonIdent personIdent,
      Optional<IdentifiedUser> currentUser) {
    super(
        currentUser,
        combinedCheckStateCache,
        combinedCheckStateUpdatedSenderFactory,
        notesFactory,
        psUtil,
        notifyResolver);
    this.repoManager = repoManager;
    this.gitRefUpdated = gitRefUpdated;
    this.retryHelper = retryHelper;
    this.noteUtil = noteUtil;
    this.checkers = checkers;
    this.personIdent = personIdent;
  }

  @Override
  protected Check createCheckImpl(CheckKey checkKey, CheckUpdate checkUpdate)
      throws DuplicateKeyException, IOException {
    try {
      return retryHelper.execute(
          RetryHelper.ActionType.PLUGIN_UPDATE,
          () -> upsertCheckInNoteDb(checkKey, checkUpdate, Operation.CREATE),
          LockFailureException.class::isInstance);
    } catch (Exception e) {
      Throwables.throwIfUnchecked(e);
      Throwables.throwIfInstanceOf(e, DuplicateKeyException.class);
      Throwables.throwIfInstanceOf(e, IOException.class);
      throw new IOException(e);
    }
  }

  @Override
  protected Check updateCheckImpl(CheckKey checkKey, CheckUpdate checkUpdate) throws IOException {
    try {
      return retryHelper.execute(
          RetryHelper.ActionType.PLUGIN_UPDATE,
          () -> upsertCheckInNoteDb(checkKey, checkUpdate, Operation.UPDATE),
          LockFailureException.class::isInstance);
    } catch (Exception e) {
      Throwables.throwIfUnchecked(e);
      Throwables.throwIfInstanceOf(e, IOException.class);
      throw new IOException(e);
    }
  }

  private Check upsertCheckInNoteDb(CheckKey checkKey, CheckUpdate checkUpdate, Operation operation)
      throws IOException, ConfigInvalidException, DuplicateKeyException {
    if (operation == Operation.CREATE) {
      assertCheckerIsPresent(checkKey.checkerUuid());
    }

    try (Repository repo = repoManager.openRepository(checkKey.repository());
        ObjectInserter objectInserter = repo.newObjectInserter();
        RevWalk rw = new RevWalk(repo)) {
      Ref checkRef = repo.getRefDatabase().exactRef(checksRef(checkKey.patchSet().changeId()));
      ObjectId parent = checkRef == null ? ObjectId.zeroId() : checkRef.getObjectId();
      CommitBuilder cb;
      String message;
      if (operation == Operation.CREATE) {
        message = "Insert check " + checkKey.checkerUuid();
        cb = commitBuilder(message, parent);
      } else {
        message = "Update check " + checkKey.checkerUuid();
        cb = commitBuilder(message, parent);
      }

      boolean dirty =
          updateNotesMap(checkKey, checkUpdate, repo, rw, objectInserter, parent, cb, operation);
      if (!dirty) {
        // This update is a NoOp, so omit writing a commit with the same tree.
        return readSingleCheck(checkKey, repo, rw, checkRef.getObjectId());
      }

      ObjectId newCommitId = objectInserter.insert(cb);
      objectInserter.flush();

      String refName = CheckerRef.checksRef(checkKey.patchSet().changeId());
      RefUpdate refUpdate = repo.updateRef(refName);
      refUpdate.setExpectedOldObjectId(parent);
      refUpdate.setNewObjectId(newCommitId);
      refUpdate.setRefLogIdent(personIdent);
      refUpdate.setRefLogMessage(message, false);
      refUpdate.update();
      RefUpdateUtil.checkResult(refUpdate);

      combinedCheckStateCache.updateIfNecessary(checkKey.repository(), checkKey.patchSet());
      gitRefUpdated.fire(
          checkKey.repository(), refUpdate, currentUser.map(user -> user.state()).orElse(null));
      return readSingleCheck(checkKey, repo, rw, newCommitId);
    }
  }

  private void assertCheckerIsPresent(CheckerUuid checkerUuid)
      throws ConfigInvalidException, IOException {
    checkers
        .getChecker(checkerUuid)
        .orElseThrow(() -> new IOException(String.format("checker %s not found", checkerUuid)));
  }

  private boolean updateNotesMap(
      CheckKey checkKey,
      CheckUpdate checkUpdate,
      Repository repo,
      RevWalk rw,
      ObjectInserter ins,
      ObjectId curr,
      CommitBuilder cb,
      Operation operation)
      throws ConfigInvalidException, IOException, DuplicateKeyException {
    Ref patchSetRef = repo.exactRef(checkKey.patchSet().toRefName());
    if (patchSetRef == null) {
      throw new IOException(String.format("patchset %s not found", checkKey.patchSet()));
    }
    ObjectId commitId = patchSetRef.getObjectId();

    // Read a fresh copy of the notes map
    Map<ObjectId, NoteDbCheckMap> newNotes = getRevisionNoteByCommitId(rw, curr);
    if (!newNotes.containsKey(commitId)) {
      if (operation == Operation.UPDATE) {
        throw new IOException(String.format("checker %s not found", checkKey.checkerUuid()));
      }

      newNotes.put(commitId, NoteDbCheckMap.empty());
    }

    NoteDbCheckMap checksForRevision = newNotes.get(commitId);
    if (!checksForRevision.checks.containsKey(checkKey.checkerUuid().get())) {
      if (operation == Operation.UPDATE) {
        throw new IOException(String.format("checker %s not found", checkKey.checkerUuid()));
      }

      // Create check
      NoteDbCheck newCheck = NoteDbCheck.createInitialNoteDbCheck(checkUpdate);
      newCheck.created = Timestamp.from(personIdent.getWhen().toInstant());
      newCheck.updated = newCheck.created;
      checksForRevision.checks.put(checkKey.checkerUuid().get(), newCheck);
      writeNotesMap(newNotes, cb, ins);
      return true;
    } else if (operation == Operation.CREATE) {
      throw new DuplicateKeyException(
          String.format("checker %s already exists", checkKey.checkerUuid()));
    }

    // Update in place
    NoteDbCheck modifiedCheck = checksForRevision.checks.get(checkKey.checkerUuid().get());
    boolean dirty = modifiedCheck.applyUpdate(checkUpdate);
    if (!dirty) {
      return false;
    }
    modifiedCheck.updated = Timestamp.from(personIdent.getWhen().toInstant());

    writeNotesMap(newNotes, cb, ins);
    return true;
  }

  private void writeNotesMap(
      Map<ObjectId, NoteDbCheckMap> notesMap, CommitBuilder cb, ObjectInserter ins)
      throws IOException {
    CheckRevisionNoteMap output = CheckRevisionNoteMap.emptyMap();
    for (Map.Entry<ObjectId, NoteDbCheckMap> e : notesMap.entrySet()) {
      ObjectId id = e.getKey();
      byte[] data = toData(e.getValue());
      if (data.length != 0) {
        ObjectId dataBlob = ins.insert(OBJ_BLOB, data);
        output.noteMap.set(id, dataBlob);
      }
    }
    cb.setTreeId(output.noteMap.writeTree(ins));
  }

  private Map<ObjectId, NoteDbCheckMap> getRevisionNoteByCommitId(RevWalk rw, ObjectId curr)
      throws ConfigInvalidException, IOException {
    CheckRevisionNoteMap existingNotes = getRevisionNoteMap(rw, curr);

    // Generate a list with all current checks keyed by patch set
    Map<ObjectId, NoteDbCheckMap> newNotes =
        Maps.newHashMapWithExpectedSize(existingNotes.revisionNotes.size());
    for (Map.Entry<ObjectId, CheckRevisionNote> e : existingNotes.revisionNotes.entrySet()) {
      newNotes.put(e.getKey(), e.getValue().getOnlyEntity());
    }
    return newNotes;
  }

  private CheckRevisionNoteMap getRevisionNoteMap(RevWalk rw, ObjectId curr)
      throws ConfigInvalidException, IOException {
    if (curr.equals(ObjectId.zeroId())) {
      return CheckRevisionNoteMap.emptyMap();
    }
    NoteMap noteMap;
    if (!curr.equals(ObjectId.zeroId())) {
      noteMap = NoteMap.read(rw.getObjectReader(), rw.parseCommit(curr));
    } else {
      noteMap = NoteMap.newEmptyMap();
    }
    return CheckRevisionNoteMap.parseChecks(
        noteUtil.getChangeNoteJson(), rw.getObjectReader(), noteMap);
  }

  private byte[] toData(NoteDbCheckMap map) throws IOException {
    if (map.checks.isEmpty()) {
      return new byte[0];
    }

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (OutputStreamWriter osw = new OutputStreamWriter(out, UTF_8)) {
      noteUtil.getChangeNoteJson().getGson().toJson(map, osw);
    }
    return out.toByteArray();
  }

  private CommitBuilder commitBuilder(String message, ObjectId parent) {
    CommitBuilder cb = new CommitBuilder();
    if (!parent.equals(ObjectId.zeroId())) {
      cb.setParentId(parent);
    }
    cb.setAuthor(personIdent);
    cb.setCommitter(personIdent);
    cb.setMessage(message);
    return cb;
  }

  private Check readSingleCheck(CheckKey checkKey, Repository repo, RevWalk rw, ObjectId tip)
      throws IOException, ConfigInvalidException {
    Ref patchSetRef = repo.exactRef(checkKey.patchSet().toRefName());
    if (patchSetRef == null) {
      throw new IllegalStateException("patchset " + checkKey.patchSet() + " not found");
    }
    ObjectId commitId = patchSetRef.getObjectId();
    Map<ObjectId, NoteDbCheckMap> newNotes = getRevisionNoteByCommitId(rw, tip);
    if (!newNotes.containsKey(commitId)) {
      throw new IllegalStateException("revision " + commitId.name() + " not found");
    }
    Map<String, NoteDbCheck> checks = newNotes.get(commitId).checks;
    String checkerUuidString = checkKey.checkerUuid().get();
    if (!checks.containsKey(checkerUuidString)) {
      throw new IllegalStateException("checker " + checkerUuidString + " not found");
    }
    return checks.get(checkerUuidString).toCheck(checkKey);
  }
}
