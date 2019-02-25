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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.server.notedb.ChangeNoteJson;
import com.google.gerrit.server.notedb.RevisionNote;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;

public class CheckRevisionNote extends RevisionNote<NoteDbCheckMap> {

  private final ChangeNoteJson noteUtil;

  CheckRevisionNote(ChangeNoteJson noteUtil, ObjectReader reader, ObjectId noteId) {
    super(reader, noteId);
    this.noteUtil = noteUtil;
  }

  // TODO(hiesel) Fix this in core. We need to use a generic to set what the collection type is.
  @Override
  protected List<NoteDbCheckMap> parse(byte[] raw, int offset)
      throws IOException, ConfigInvalidException {
    try (InputStream is = new ByteArrayInputStream(raw, offset, raw.length - offset);
        Reader r = new InputStreamReader(is, UTF_8)) {
      return ImmutableList.of(noteUtil.getGson().fromJson(r, NoteDbCheckMap.class));
    }
  }
}
