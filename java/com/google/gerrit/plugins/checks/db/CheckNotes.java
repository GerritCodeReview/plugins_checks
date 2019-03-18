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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.plugins.checks.CheckerRef;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.logging.TraceContext.TraceTimer;
import com.google.gerrit.server.notedb.AbstractChangeNotes;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevCommit;

public class CheckNotes extends AbstractChangeNotes<CheckRevisionNote> {
  public interface Factory {
    /**
     * Creates a {@link CheckNotes} instance that parses checks for all revisions on load.
     *
     * @param change the change for which checks should be loaded
     * @return the create CheckNotes instance
     */
    CheckNotes createForAllRevisions(Change change);

    /**
     * Creates a {@link CheckNotes} instance that parses checks only for the specified revision.
     *
     * <p>Trying to access checks for any other revision through {@link CheckNotes#getChecks(RevId)}
     * or trying to access checks for all revisions through {@link CheckNotes#getAllChecks()} fails
     * with a {@link IllegalStateException}.
     *
     * @param change the change for which checks should be loaded
     * @param revId the revision for which checks should be loaded
     * @return the create CheckNotes instance
     */
    CheckNotes createForSingleRevision(Change change, RevId revId);
  }

  private final Change change;
  private final Optional<RevId> revId;

  private ImmutableMap<RevId, NoteDbCheckMap> entities;
  private ObjectId metaId;

  @AssistedInject
  CheckNotes(Args args, @Assisted Change change) {
    super(args, change.getId());
    this.change = change;
    this.revId = Optional.empty();
  }

  @AssistedInject
  CheckNotes(Args args, @Assisted Change change, @Assisted RevId revId) {
    super(args, change.getId());
    this.change = change;
    this.revId = Optional.of(revId);
  }

  /**
   * Get the checks for the specified revision.
   *
   * <p>If this ChangeNotes instance was created for a specific revision via {@link
   * CheckNotes.Factory#createForSingleRevision(Change, RevId)} and another revision is passed in
   * this method fails with {@link IllegalStateException}.
   *
   * <p>If no checks exist for the specified revision an empty NoteDbCheckMap is returned.
   *
   * @param revId the revision for which the checks should be returned
   * @return the checks for the specified revision
   */
  public NoteDbCheckMap getChecks(RevId revId) {
    if (this.revId.isPresent()) {
      checkState(
          this.revId.get().equals(revId),
          "revId mismatch, loaded checks for revision %s, but requesting checks for revision %s",
          this.revId.get(),
          revId);
    }
    return entities.getOrDefault(revId, NoteDbCheckMap.empty());
  }

  /**
   * Gets the checks for all revisions.
   *
   * <p>If this ChangeNotes instance was created for a specific revision via {@link
   * CheckNotes.Factory#createForSingleRevision(Change, RevId)} this method fails with {@link
   * IllegalStateException}.
   *
   * <p>Revisions for which no checks exist are not present in the returned map.
   *
   * @return all checks by revision ID
   */
  public ImmutableMap<RevId, NoteDbCheckMap> getAllChecks() {
    checkState(
        !revId.isPresent(),
        "checks were loaded for revision %s, but requesting checks for all revisions",
        revId.get());
    return entities;
  }

  @Override
  public String getRefName() {
    return CheckerRef.checksRef(getChangeId());
  }

  @Nullable
  public ObjectId getMetaId() {
    return metaId;
  }

  @Override
  protected void onLoad(LoadHandle handle) throws IOException, ConfigInvalidException {
    metaId = handle.id();
    if (metaId == null) {
      loadDefaults();
      return;
    }
    metaId = metaId.copy();

    NoteMap revisionNoteMap;
    try (TraceTimer ignored =
        TraceContext.newTimer(
            "Load check notes for change %s of project %s", getChangeId(), getProjectName())) {
      RevCommit tipCommit = handle.walk().parseCommit(metaId);
      ObjectReader reader = handle.walk().getObjectReader();
      revisionNoteMap = NoteMap.read(reader, tipCommit);

      ImmutableMap.Builder<RevId, NoteDbCheckMap> entitiesBuilder = ImmutableMap.builder();
      if (revId.isPresent()) {
        CheckRevisionNoteMap.parseChecksForSingleRevision(
                revId.get(), args.changeNoteJson, handle.walk().getObjectReader(), revisionNoteMap)
            .ifPresent(rn -> entitiesBuilder.put(revId.get(), rn.getOnlyEntity()));
      } else {
        CheckRevisionNoteMap.parseChecksForAllRevisions(
                args.changeNoteJson, reader, revisionNoteMap)
            .revisionNotes
            .entrySet()
            .stream()
            .forEach(e -> entitiesBuilder.put(e.getKey(), e.getValue().getOnlyEntity()));
      }
      entities = entitiesBuilder.build();
    }
  }

  @Override
  protected void loadDefaults() {
    entities = ImmutableMap.of();
  }

  @Override
  public Project.NameKey getProjectName() {
    return change.getProject();
  }
}
