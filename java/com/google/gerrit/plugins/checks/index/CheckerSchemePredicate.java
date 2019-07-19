package com.google.gerrit.plugins.checks.index;

import static java.util.Objects.requireNonNull;

import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.plugins.checks.Check;

public class CheckerSchemePredicate extends CheckPredicate {
  private final String checkerScheme;

  public CheckerSchemePredicate(String checkerScheme) {
    super(CheckQueryBuilder.FIELD_SCHEME, checkerScheme);
    this.checkerScheme = requireNonNull(checkerScheme, "checkerScheme");
  }

  @Override
  public boolean match(Check check) throws StorageException {
    return checkerScheme.equals(check.key().checkerUuid().scheme());
  }

  public String getCheckerScheme() {
    return checkerScheme;
  }
}
