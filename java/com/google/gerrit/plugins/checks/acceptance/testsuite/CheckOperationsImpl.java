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

import static com.google.common.base.Preconditions.checkNotNull;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.git.RefUpdateUtil;
import com.google.gerrit.plugins.checks.Check;
import com.google.gerrit.plugins.checks.CheckJson;
import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.CheckUpdate;
import com.google.gerrit.plugins.checks.CheckerRef;
import com.google.gerrit.plugins.checks.Checks;
import com.google.gerrit.plugins.checks.Checks.GetCheckOptions;
import com.google.gerrit.plugins.checks.ChecksUpdate;
import com.google.gerrit.plugins.checks.ListChecksOption;
import com.google.gerrit.plugins.checks.api.CheckInfo;
import com.google.gerrit.plugins.checks.db.NoteDbCheck;
import com.google.gerrit.plugins.checks.db.NoteDbCheckMap;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNoteJson;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.function.Consumer;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.Note;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;

public final class CheckOperationsImpl implements CheckOperations {
  private final Checks checks;
  private final Provider<ChecksUpdate> checksUpdate;
  private final CheckJson.Factory checkJsonFactory;
  private final GitRepositoryManager repoManager;
  private final ChangeNotes.Factory changeNotesFactory;
  private final PatchSetUtil patchSetUtil;
  private final ChangeNoteJson changeNoteJson;
  private final Provider<PersonIdent> serverIdentProvider;

  @Inject
  public CheckOperationsImpl(
      Checks checks,
      @ServerInitiated Provider<ChecksUpdate> checksUpdate,
      CheckJson.Factory checkJsonFactory,
      GitRepositoryManager repoManager,
      ChangeNotes.Factory changeNotesFactory,
      PatchSetUtil patchSetUtil,
      ChangeNoteJson changeNoteJson,
      @GerritPersonIdent Provider<PersonIdent> serverIdentProvider) {
    this.checks = checks;
    this.checksUpdate = checksUpdate;
    this.checkJsonFactory = checkJsonFactory;
    this.repoManager = repoManager;
    this.changeNotesFactory = changeNotesFactory;
    this.patchSetUtil = patchSetUtil;
    this.changeNoteJson = changeNoteJson;
    this.serverIdentProvider = serverIdentProvider;
  }

  @Override
  public PerCheckOperations check(CheckKey key) {
    return new PerCheckOperationsImpl(key);
  }

  @Override
  public TestCheckUpdate.Builder newCheck(CheckKey key) {
    return TestCheckUpdate.builder(key)
        .checkUpdater(u -> checksUpdate.get().createCheck(key, toCheckUpdate(u)));
  }

  final class PerCheckOperationsImpl implements PerCheckOperations {
    private final CheckKey key;

    PerCheckOperationsImpl(CheckKey key) {
      this.key = key;
    }

    @Override
    public boolean exists() throws Exception {
      return checks.getCheck(key, GetCheckOptions.defaults()).isPresent();
    }

    @Override
    public Check get() throws Exception {
      return checks
          .getCheck(key, GetCheckOptions.defaults())
          .orElseThrow(() -> new IllegalStateException("Tried to get non-existing test check"));
    }

    @Override
    public ImmutableMap<RevId, String> notesAsText() throws Exception {
      try (Repository repo = repoManager.openRepository(key.repository());
          RevWalk rw = new RevWalk(repo)) {
        Ref checkRef =
            repo.getRefDatabase().exactRef(CheckerRef.checksRef(key.patchSet().changeId));
        checkNotNull(checkRef);

        NoteMap notes = NoteMap.read(rw.getObjectReader(), rw.parseCommit(checkRef.getObjectId()));
        ImmutableMap.Builder<RevId, String> raw = ImmutableMap.builder();

        for (Note note : notes) {
          raw.put(
              new RevId(note.name()),
              new String(notes.getCachedBytes(note.toObjectId(), Integer.MAX_VALUE)));
        }
        return raw.build();
      }
    }

    @Override
    public CheckInfo asInfo(ListChecksOption... options) throws Exception {
      return checkJsonFactory.create(ImmutableSet.copyOf(options)).format(get());
    }

    @Override
    public TestCheckUpdate.Builder forUpdate() {
      return TestCheckUpdate.builder(key)
          .checkUpdater(
              testUpdate -> checksUpdate.get().updateCheck(key, toCheckUpdate(testUpdate)));
    }

    @Override
    public TestCheckInvalidation.Builder forInvalidation() {
      return TestCheckInvalidation.builder(this::invalidateCheck);
    }

    private void invalidateCheck(TestCheckInvalidation testCheckInvalidation) throws Exception {
      if (testCheckInvalidation.invalidState()) {
        updateJson(c -> c.unsafeSetStateTestOnly("invalid"));
      }

      if (testCheckInvalidation.invalidCreated()) {
        updateJson(c -> c.unsafeSetCreatedTestOnly("invalid"));
      }

      if (testCheckInvalidation.invalidUpdated()) {
        updateJson(c -> c.unsafeSetUpdatedTestOnly("invalid"));
      }

      if (testCheckInvalidation.invalidStarted()) {
        updateJson(c -> c.unsafeSetStartedTestOnly("invalid"));
      }

      if (testCheckInvalidation.invalidFinished()) {
        updateJson(c -> c.unsafeSetFinishedTestOnly("invalid"));
      }

      if (testCheckInvalidation.unsetState()) {
        updateJson(c -> c.unsafeSetStateTestOnly(null));
      }

      if (testCheckInvalidation.unsetCreated()) {
        updateJson(c -> c.unsafeSetCreatedTestOnly(null));
      }

      if (testCheckInvalidation.unsetUpdated()) {
        updateJson(c -> c.unsafeSetUpdatedTestOnly(null));
      }
    }

    private void updateJson(Consumer<NoteDbCheck> checkUpdater) throws Exception {
      try (Repository repo = repoManager.openRepository(key.repository());
          RevWalk rw = new RevWalk(repo);
          ObjectReader reader = repo.newObjectReader();
          ObjectInserter ins = repo.newObjectInserter()) {
        // read note map
        String checkRefName = CheckerRef.checksRef(key.patchSet().changeId);
        Ref checkRef = repo.getRefDatabase().exactRef(checkRefName);
        RevCommit baseCommit = rw.parseCommit(checkRef.getObjectId());
        NoteMap notes = NoteMap.read(reader, baseCommit);

        // read check json for patch set
        ChangeNotes changeNotes = changeNotesFactory.createChecked(key.patchSet().changeId);
        PatchSet patchSet = patchSetUtil.get(changeNotes, key.patchSet());
        ObjectId noteId = ObjectId.fromString(patchSet.getRevision().get());
        ObjectId blobId = notes.get(noteId);
        String json =
            new String(reader.open(blobId, OBJ_BLOB).getCachedBytes(Integer.MAX_VALUE), UTF_8);

        // parse check map
        NoteDbCheckMap noteDbCheckMap =
            changeNoteJson.getGson().fromJson(json, NoteDbCheckMap.class);
        NoteDbCheck noteDbCheck = noteDbCheckMap.checks.get(key.checkerUuid().get());

        // update check
        checkUpdater.accept(noteDbCheck);

        // update check json for patch set
        notes.set(noteId, changeNoteJson.getGson().toJson(noteDbCheckMap), ins);

        // create commit
        PersonIdent serverIdent = serverIdentProvider.get();
        CommitBuilder b = new CommitBuilder();
        b.setTreeId(notes.writeTree(ins));
        b.setAuthor(serverIdent);
        b.setCommitter(serverIdent);
        b.setParentIds(baseCommit);
        b.setMessage("Invalidate test check");
        ObjectId commitId = ins.insert(b);
        ins.flush();
        RevCommit newCommit = rw.parseCommit(commitId);

        // update notes branch
        BatchRefUpdate bru = repo.getRefDatabase().newBatchUpdate();
        bru.addCommand(new ReceiveCommand(baseCommit, newCommit, checkRefName));
        RefUpdateUtil.executeChecked(bru, rw);
      }
    }
  }

  private static CheckUpdate toCheckUpdate(TestCheckUpdate testUpdate) {
    CheckUpdate.Builder update = CheckUpdate.builder();
    testUpdate.message().ifPresent(update::setMessage);
    testUpdate.state().ifPresent(update::setState);
    testUpdate.started().ifPresent(update::setStarted);
    testUpdate.finished().ifPresent(update::setFinished);
    testUpdate.url().ifPresent(update::setUrl);
    return update.build();
  }
}
