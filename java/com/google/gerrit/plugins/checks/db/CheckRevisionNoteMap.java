// Copyright (C) 2016 The Android Open Source Project
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

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.server.notedb.ChangeNoteJson;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.notes.Note;
import org.eclipse.jgit.notes.NoteMap;

public class CheckRevisionNoteMap {
  final NoteMap noteMap;
  final ImmutableMap<RevId, CheckRevisionNote> revisionNotes;

  static CheckRevisionNoteMap parseChecksForAllRevisions(
      ChangeNoteJson changeNoteJson, ObjectReader reader, NoteMap noteMap)
      throws ConfigInvalidException, IOException {
    Map<RevId, CheckRevisionNote> result = new HashMap<>();
    for (Note note : noteMap) {
      CheckRevisionNote rn = new CheckRevisionNote(changeNoteJson, reader, note.getData());
      rn.parse();
      result.put(new RevId(note.name()), rn);
    }
    return new CheckRevisionNoteMap(noteMap, ImmutableMap.copyOf(result));
  }

  static Optional<CheckRevisionNote> parseChecksForSingleRevision(
      RevId revId, ChangeNoteJson changeNoteJson, ObjectReader reader, NoteMap noteMap)
      throws IOException, ConfigInvalidException {
    ObjectId noteData = noteMap.get(ObjectId.fromString(revId.get()));
    if (noteData == null) {
      return Optional.empty();
    }
    CheckRevisionNote rn = new CheckRevisionNote(changeNoteJson, reader, noteData);
    rn.parse();
    return Optional.of(rn);
  }

  static CheckRevisionNoteMap emptyMap() {
    return new CheckRevisionNoteMap(NoteMap.newEmptyMap(), ImmutableMap.of());
  }

  private CheckRevisionNoteMap(
      NoteMap noteMap, ImmutableMap<RevId, CheckRevisionNote> revisionNotes) {
    this.noteMap = noteMap;
    this.revisionNotes = revisionNotes;
  }
}
