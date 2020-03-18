package com.google.gerrit.plugins.checks;

import com.google.gerrit.plugins.checks.api.CheckerStatus;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import com.google.inject.Provider;

public class CheckerRequiredForSubmit {

  private final Provider<CheckerQuery> checkerQueryProvider;

  @Inject
  CheckerRequiredForSubmit(Provider<CheckerQuery> checkerQueryProvider) {
    this.checkerQueryProvider = checkerQueryProvider;
  }

  public boolean isRequiredForSubmit(Checker checker, ChangeData changeData) {
    return checker.getStatus() == CheckerStatus.ENABLED
        && checker.isRequired()
        && checkerQueryProvider.get().isCheckerRelevant(checker, changeData);
  }
}
