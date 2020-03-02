package com.google.gerrit.plugins.checks.db;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.plugins.checks.Check;
import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.CheckOverride;
import com.google.gerrit.plugins.checks.CheckUpdate;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.api.CheckState;
import com.google.gerrit.server.util.time.TimeUtil;
import java.sql.Timestamp;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Representation of {@link Check} that can be serialized with GSON. */
class NoteDbCheck {

  private NoteDbCheck() {}

  public CheckState state = CheckState.NOT_STARTED;
  @Nullable public String message;
  @Nullable public String url;
  @Nullable public Timestamp started;
  @Nullable public Timestamp finished;
  @Nullable public Set<CheckOverrideForGson> overrides;

  private class CheckOverrideForGson {
    public Integer overrider;
    public String reason;
    public Timestamp created;
  }

  private CheckOverride toCheckOverride(CheckOverrideForGson tmp) {
    CheckOverride.Builder checkOverride =
        CheckOverride.builder()
            .setCreated(tmp.created)
            .setOverrider(Account.id(tmp.overrider))
            .setReason(tmp.reason);
    return checkOverride.build();
  }

  private CheckOverrideForGson toCheckOverrideForGson(CheckOverride checkOverride) {
    CheckOverrideForGson tmp = new CheckOverrideForGson();
    tmp.created = checkOverride.created();
    tmp.overrider = checkOverride.overrider().get();
    tmp.reason = checkOverride.reason();
    return tmp;
  }

  public Timestamp created;
  public Timestamp updated;

  Check toCheck(CheckKey key) {
    Check.Builder newCheck =
        Check.builder(key).setState(state).setCreated(created).setUpdated(updated);
    if (message != null) {
      newCheck.setMessage(message);
    }
    if (url != null) {
      newCheck.setUrl(url);
    }
    if (started != null) {
      newCheck.setStarted(started);
    }
    if (finished != null) {
      newCheck.setFinished(finished);
    }
    if (overrides != null) {
      newCheck.setOverrides(
          overrides.stream()
              .map(override -> toCheckOverride(override))
              .collect(Collectors.toSet()));
    } else {
      newCheck.setOverrides(ImmutableSet.of());
    }
    return newCheck.build();
  }

  Check toCheck(Project.NameKey repositoryName, PatchSet.Id patchSetId, CheckerUuid checkerUuid) {
    CheckKey key = CheckKey.create(repositoryName, patchSetId, checkerUuid);
    return toCheck(key);
  }

  static NoteDbCheck createInitialNoteDbCheck(CheckUpdate checkUpdate) {
    NoteDbCheck noteDbCheck = new NoteDbCheck();
    noteDbCheck.applyUpdate(checkUpdate);
    return noteDbCheck;
  }

  /**
   * Applies the given update and returns {@code true} if at least a single fields value was changed
   * to a different value, {@code false} otherwise. Does not update timestamps.
   */
  boolean applyUpdate(CheckUpdate update) {
    boolean modified = false;
    if (update.state().isPresent() && !update.state().get().equals(state)) {
      state = update.state().get();
      modified = true;
    }
    if (update.message().isPresent()
        && !update.message().get().equals(Strings.nullToEmpty(message))) {
      message = Strings.emptyToNull(update.message().get());
      modified = true;
    }
    if (update.url().isPresent() && !update.url().get().equals(Strings.nullToEmpty(url))) {
      url = Strings.emptyToNull(update.url().get());
      modified = true;
    }
    if (update.started().isPresent() && !update.started().get().equals(started)) {
      if (update.started().get().equals(TimeUtil.never())) {
        started = null;
      } else {
        started = update.started().get();
      }
      modified = true;
    }
    if (update.finished().isPresent() && !update.finished().get().equals(finished)) {
      if (update.finished().get().equals(TimeUtil.never())) {
        finished = null;
      } else {
        finished = update.finished().get();
      }
      modified = true;
    }
    if (overrides == null) {
      overrides = new HashSet<>();
    }
    Set<CheckOverrideForGson> newOverrides =
        update.overridesModification()
            .apply(
                ImmutableSet.copyOf(
                    overrides.stream()
                        .map(override -> toCheckOverride(override))
                        .collect(Collectors.toSet())))
            .stream()
            .map(override -> toCheckOverrideForGson(override))
            .collect(Collectors.toSet());
    if (!overrides.equals(newOverrides)) {
      overrides = newOverrides;
      modified = true;
    }
    return modified;
  }
}
