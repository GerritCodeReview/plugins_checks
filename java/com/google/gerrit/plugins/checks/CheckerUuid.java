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

import static com.google.common.base.Preconditions.checkArgument;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Comparator.comparing;

import com.google.auto.value.AutoValue;
import com.google.common.base.Ascii;
import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import com.google.common.hash.Hashing;
import com.google.gerrit.common.Nullable;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * UUID of a checker.
 *
 * <p>UUIDs are of the form {code SCHEME ':' ID}, where:
 *
 * <ul>
 *   <li>Scheme is an RFC 3986 compliant scheme, stored in lowercase. By convention, checkers
 *       created by the same external system (e.g. Jenkins) share a scheme name.
 *   <li>ID is an arbitrary string provided by the external system, and can contain any characters
 *       other than {@code '\n'} and {@code '\0'}.
 * </ul>
 */
@AutoValue
public abstract class CheckerUuid implements Comparable<CheckerUuid> {
  // https://tools.ietf.org/html/rfc3986#section-3.1
  // scheme = ALPHA *( ALPHA / DIGIT / "+" / "-" / "." )
  private static final Pattern SCHEME_PATTERN =
      Pattern.compile("^[a-z][a-z0-9+.-]*$", Pattern.CASE_INSENSITIVE);

  private static final CharMatcher DISALLOWED_UUID_CHARS = CharMatcher.anyOf("\n\0");

  /**
   * Creates a new UUID with the given scheme and ID portions.
   *
   * @param scheme RFC 3986 compliant scheme. Will be converted to lowercase.
   * @param id arbitrary ID portion.
   * @return new UUID.
   */
  public static CheckerUuid create(String scheme, String id) {
    checkArgument(isScheme(scheme), "invalid scheme: %s", scheme);
    checkArgument(isId(id), "invalid id: %s", id);
    return new AutoValue_CheckerUuid(Ascii.toLowerCase(scheme), id);
  }

  /**
   * Attempts to parse the given UUID string into a {@code CheckerUuid}.
   *
   * @param uuid UUID string.
   * @return new UUID if {@code uuid} is a valid UUID, or empty otherwise.
   */
  public static Optional<CheckerUuid> tryParse(@Nullable String uuid) {
    if (!isUuid(uuid)) {
      return Optional.empty();
    }
    int colon = uuid.indexOf(':');
    String scheme = uuid.substring(0, colon);
    if (!isScheme(scheme)) {
      return Optional.empty();
    }
    String id = uuid.substring(colon + 1);
    if (!isId(id)) {
      return Optional.empty();
    }
    // Bypass redundant checks in #create(String, String).
    return Optional.of(new AutoValue_CheckerUuid(Ascii.toLowerCase(scheme), id));
  }

  /**
   * Returns whether the given input is a valid UUID string.
   *
   * @param uuid UUID string.
   * @return true if {@code uuid} is a valid UUID, false otherwise.
   */
  public static boolean isUuid(@Nullable String uuid) {
    if (uuid == null) {
      return false;
    }
    int colon = uuid.indexOf(':');
    return colon >= 0 && isScheme(uuid.substring(0, colon)) && isId(uuid.substring(colon));
  }

  private static boolean isId(String id) {
    return DISALLOWED_UUID_CHARS.matchesNoneOf(id);
  }

  private static boolean isScheme(@Nullable String scheme) {
    return !Strings.isNullOrEmpty(scheme) && SCHEME_PATTERN.matcher(scheme).find();
  }

  /**
   * Parses the given UUID string into a {@code CheckerUuid}, throwing an unchecked exception if it
   * is not in the proper format..
   *
   * @param uuid UUID string.
   * @return new UUID.
   */
  public static CheckerUuid parse(String checkerUuid) {
    return tryParse(checkerUuid)
        .orElseThrow(() -> new IllegalArgumentException("invalid checker UUID: " + checkerUuid));
  }

  /**
   * Scheme portion of the UUID.
   *
   * @return the scheme, always lowercase.
   */
  public abstract String scheme();

  /**
   * ID portion of the UUID.
   *
   * @return the ID.
   */
  public abstract String id();

  /**
   * Computes the SHA-1 of the UUID, for use in the Git storage layer where SHA-1s are used as keys.
   *
   * @return hex SHA-1 of this UUID's string representation.
   */
  @SuppressWarnings("deprecation") // SHA-1 used where Git object IDs are required.
  public String sha1() {
    return Hashing.sha1().hashString(toString(), UTF_8).toString();
  }

  @Override
  public String toString() {
    return scheme() + ':' + id();
  }

  @Override
  public int compareTo(CheckerUuid o) {
    return comparing(CheckerUuid::toString).compare(this, o);
  }
}
