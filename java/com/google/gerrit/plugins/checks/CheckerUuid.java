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

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Comparator.comparing;

import com.google.auto.value.AutoValue;
import com.google.common.base.Ascii;
import com.google.common.hash.Hashing;
import com.google.gerrit.common.Nullable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

/**
 * UUID of a checker.
 *
 * <p>UUIDs are absolute <a href="https://tools.ietf.org/html/rfc3986#section-1.2.3">opaque URIs</a>
 * of the form {@code SCHEME ':' ID}, where:
 *
 * <ul>
 *   <li>Scheme is a URI scheme, stored in lowercase. By convention, checkers created by the same
 *       external system (e.g. Jenkins) share a scheme name.
 *   <li>ID is an arbitrary string provided by the external system. It must not start with a slash,
 *       and must use proper URL encoding, but is otherwise not interpreted by Gerrit.
 * </ul>
 *
 * The URI is not intended to have a normal network protocol as its scheme, and is not intended to
 * locate anything on the internet. It is just an arbitrary user-defined scheme plus an opaque
 * string.
 */
@AutoValue
public abstract class CheckerUuid implements Comparable<CheckerUuid> {
  /**
   * Attempts to parse the given UUID string into a {@code CheckerUuid}.
   *
   * @param uuid UUID string.
   * @return new UUID if {@code uuid} is a valid UUID, or empty otherwise.
   */
  public static Optional<CheckerUuid> tryParse(@Nullable String uuid) {
    if (uuid == null) {
      return Optional.empty();
    }
    URI uri;
    try {
      uri = new URI(uuid);
    } catch (URISyntaxException e) {
      return Optional.empty();
    }
    if (uri.getScheme() == null || !uri.isOpaque()) {
      return Optional.empty();
    }

    // Lowercase scheme and normalize escape sequences.
    String lowerScheme = Ascii.toLowerCase(uri.getScheme());
    try {
      uri = new URI(lowerScheme, uri.getSchemeSpecificPart(), uri.getFragment());
    } catch (URISyntaxException e) {
      throw new IllegalStateException("Invalid URI after normalization", e);
    }
    return Optional.of(new AutoValue_CheckerUuid(uri));
  }

  /**
   * Returns whether the given input is a valid UUID string.
   *
   * @param uuid UUID string.
   * @return true if {@code uuid} is a valid UUID, false otherwise.
   */
  public static boolean isUuid(@Nullable String uuid) {
    return tryParse(uuid).isPresent();
  }

  /**
   * Parses the given UUID string into a {@code CheckerUuid}, throwing an unchecked exception if it
   * is not in the proper format..
   *
   * @param uuid UUID string.
   * @return new UUID.
   */
  public static CheckerUuid parse(String uuid) {
    return tryParse(uuid)
        .orElseThrow(() -> new IllegalArgumentException("invalid checker UUID: " + uuid));
  }

  /**
   * Underlying URI representation.
   *
   * <p>The fact that this class is implemented using {@link java.net.URI} is an implementation
   * detail; callers should use the public methods such ass {@link #scheme()} and {@link
   * #toString()}.
   *
   * @return the scheme, always lowercase.
   */
  abstract URI uri();

  /**
   * Scheme portion of the UUID.
   *
   * @return the scheme, in lowercase.
   */
  public String scheme() {
    return uri().getScheme();
  }

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
    return uri().toString();
  }

  @Override
  public int compareTo(CheckerUuid o) {
    return comparing(CheckerUuid::uri).compare(this, o);
  }
}
