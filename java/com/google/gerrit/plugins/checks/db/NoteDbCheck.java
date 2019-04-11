package com.google.gerrit.plugins.checks.db;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Enums;
import com.google.common.base.Strings;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.plugins.checks.Check;
import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.CheckUpdate;
import com.google.gerrit.plugins.checks.api.CheckState;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import org.eclipse.jgit.errors.ConfigInvalidException;

/** Representation of {@link Check} that can be serialized with GSON. */
@VisibleForTesting
public class NoteDbCheck {

  private NoteDbCheck() {}

  // All fields should be of type String so that parsing the JSON doesn't fail if fields contain
  // invalid values (e.g. if created contains a malformed date-time string).
  @Nullable private String state = CheckState.NOT_STARTED.name();
  @Nullable private String created;
  @Nullable private String updated;
  @Nullable private String message;
  @Nullable private String url;
  @Nullable private String started;
  @Nullable private String finished;

  /**
   * Sets state without type check.
   *
   * <p>Tests may use this method to set an invalid value for the state field or to unset the
   * required state field.
   *
   * <p><strong>Note:</strong> This method is intended to be only used by tests.
   *
   * @param state raw state string
   */
  @VisibleForTesting
  public void unsafeSetStateTestOnly(@Nullable String state) {
    this.state = state;
  }

  /**
   * Sets created without type check.
   *
   * <p>Tests may use this method to set an invalid value for the started field or to unset the
   * required started field.
   *
   * <p><strong>Note:</strong> This method is intended to be only used by tests.
   *
   * @param created raw created string
   */
  @VisibleForTesting
  public void unsafeSetCreatedTestOnly(@Nullable String created) {
    this.created = created;
  }

  /**
   * Sets updated without type check.
   *
   * <p>Tests may use this method to set an invalid value for the updated field or to unset the
   * required updated field.
   *
   * <p><strong>Note:</strong> This method is intended to be only used by tests.
   *
   * @param updated raw updated string
   */
  @VisibleForTesting
  public void unsafeSetUpdatedTestOnly(@Nullable String updated) {
    this.updated = updated;
  }

  /**
   * Sets started without type check.
   *
   * <p>Tests may use this method to set an invalid value for the started field.
   *
   * <p><strong>Note:</strong> This method is intended to be only used by tests.
   *
   * @param started raw started string
   */
  @VisibleForTesting
  public void unsafeSetStartedTestOnly(@Nullable String started) {
    this.started = started;
  }

  /**
   * Sets finished without type check.
   *
   * <p>Tests may use this method to set an invalid value for the finished field.
   *
   * <p><strong>Note:</strong> This method is intended to be only used by tests.
   *
   * @param finished raw finished string
   */
  @VisibleForTesting
  public void unsafeSetFinishedTestOnly(@Nullable String finished) {
    this.finished = finished;
  }

  void setCreated(Timestamp created) {
    this.created = formatTimestamp(created);
  }

  void setUpdated(Timestamp updated) {
    this.updated = formatTimestamp(updated);
  }

  /**
   * Create as a {@link Check} with the data that was read from NoteDb.
   *
   * @param checkKey the key of the check
   * @return the created {@link Check}
   * @throws ConfigInvalidException if the check data in NoteDb is invalid or incomplete
   */
  Check toCheck(CheckKey checkKey) throws ConfigInvalidException {
    FieldParser parser = new FieldParser(checkKey);
    Check.Builder check =
        Check.builder(checkKey)
            .setState(parser.state())
            .setCreated(parser.created())
            .setUpdated(parser.updated());
    if (message != null) {
      check.setMessage(message);
    }
    if (url != null) {
      check.setUrl(url);
    }
    if (started != null) {
      check.setStarted(parser.started());
    }
    if (finished != null) {
      check.setFinished(parser.finished());
    }
    return check.build();
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
    if (update.state().isPresent() && !update.state().get().name().equals(state)) {
      state = update.state().get().name();
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
    if (update.started().isPresent() && !formatTimestamp(update.started().get()).equals(started)) {
      started = formatTimestamp(update.started().get());
      modified = true;
    }
    if (update.finished().isPresent()
        && !formatTimestamp(update.finished().get()).equals(finished)) {
      finished = formatTimestamp(update.finished().get());
      modified = true;
    }
    return modified;
  }

  private static String formatTimestamp(Timestamp timestamp) {
    return Instant.ofEpochMilli(timestamp.getTime()).toString();
  }

  /**
   * Parser to convert string values, that were read from NoteDb, into the expected types. If the
   * conversion fails an ConfigInvalidException is thrown. The message of the ConfigInvalidException
   * states for which field the parsing failed so that invalid/missing data can be easily
   * identified.
   */
  private class FieldParser {
    private final CheckKey checkKey;

    FieldParser(CheckKey checkKey) {
      this.checkKey = checkKey;
    }

    CheckState state() throws ConfigInvalidException {
      requireNonNull(
          state,
          "state should never be null, if not set in the JSON state should default to NOT_STARTED");
      return Enums.getIfPresent(CheckState.class, state)
          .toJavaUtil()
          .orElseThrow((() -> invalidFieldValueException("state", state)));
    }

    Timestamp created() throws ConfigInvalidException {
      return parseTimestamp("created", created);
    }

    Timestamp updated() throws ConfigInvalidException {
      return parseTimestamp("updated", updated);
    }

    Timestamp started() throws ConfigInvalidException {
      return parseTimestamp("started", started);
    }

    Timestamp finished() throws ConfigInvalidException {
      return parseTimestamp("finished", finished);
    }

    private Timestamp parseTimestamp(String fieldName, @Nullable String formattedTimestamp)
        throws ConfigInvalidException {
      if (formattedTimestamp == null) {
        throw missingFieldException(fieldName);
      }
      try {
        return Timestamp.from(Instant.parse(formattedTimestamp));
      } catch (DateTimeParseException e) {
        throw invalidFieldValueException(fieldName, formattedTimestamp);
      }
    }

    private ConfigInvalidException missingFieldException(String fieldName) {
      return new ConfigInvalidException(
          String.format(
              "check %s is invalid: required field '%s' is not set",
              checkKey.getLoggableKey(), fieldName));
    }

    private ConfigInvalidException invalidFieldValueException(
        String fieldName, String invalidValue) {
      return new ConfigInvalidException(
          String.format(
              "check %s is invalid: field '%s' has invalid value '%s'",
              checkKey.getLoggableKey(), fieldName, invalidValue));
    }
  }
}
