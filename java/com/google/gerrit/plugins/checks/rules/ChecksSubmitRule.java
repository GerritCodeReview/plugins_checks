/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.gerrit.plugins.checks.rules;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.common.data.SubmitRecord.Status;
import com.google.gerrit.common.data.SubmitRequirement;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.plugins.checks.Checker;
import com.google.gerrit.plugins.checks.Checkers;
import com.google.gerrit.plugins.checks.api.BlockingCondition;
import com.google.gerrit.plugins.checks.api.CheckInfo;
import com.google.gerrit.plugins.checks.api.CheckState;
import com.google.gerrit.plugins.checks.api.CheckerStatus;
import com.google.gerrit.plugins.checks.api.CombinedCheckState;
import com.google.gerrit.plugins.checks.api.ListChecks;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.project.SubmitRuleOptions;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.rules.SubmitRule;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;

@Singleton
public class ChecksSubmitRule implements SubmitRule {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final SubmitRequirement DEFAULT_SUBMIT_REQUIREMENT_FOR_CHECKS =
      SubmitRequirement.builder()
          .setFallbackText("Passing all blocking checks required")
          .setType("passing_all_blocking_checks")
          .build();

  public static class Module extends FactoryModule {
    @Override
    public void configure() {
      bind(SubmitRule.class)
          .annotatedWith(Exports.named("ChecksSubmitRule"))
          .to(ChecksSubmitRule.class);
    }
  }

  private final ListChecks listChecks;
  private final Checkers checkers;

  @Inject
  public ChecksSubmitRule(ListChecks listChecks, Checkers checkers) {
    this.listChecks = listChecks;
    this.checkers = checkers;
  }

  @Override
  public Collection<SubmitRecord> evaluate(ChangeData changeData, SubmitRuleOptions options) {
    Project.NameKey project = changeData.project();
    Change.Id changeId = changeData.getId();
    // Gets all check results of the given change.
    ImmutableMap<String, CheckInfo> checks;
    try {
      checks =
          listChecks
              .getAllChecks(project, changeData.notes(), changeData.currentPatchSet().getId())
              .stream()
              .collect(ImmutableMap.toImmutableMap(c -> c.checkerUuid, c -> c));
    } catch (OrmException | IOException e) {
      String errorMessage = String.format("failed to get all checks for change %s", changeId);
      logger.atSevere().withCause(e).log(errorMessage);
      return singletonRecordForRuleError(errorMessage);
    }

    // Gets all checkers applicable to the given change.
    ImmutableMap<String, Checker> appliedCheckers;
    try {
      appliedCheckers =
          checkers
              .checkersOf(project)
              .stream()
              .collect(ImmutableMap.toImmutableMap(c -> c.getUuid().toString(), c -> c));
    } catch (IOException e) {
      String errorMessage =
          String.format("failed to get all checkers applied for change %s", changeId);
      logger.atSevere().log(errorMessage);
      return singletonRecordForRuleError(errorMessage);
    }

    CombinedCheckState combinedCheckState =
        getCombinedCheckState(checks, appliedCheckers, changeId);

    SubmitRecord submitRecord = new SubmitRecord();
    if (combinedCheckState.isPassing()) {
      submitRecord.status = Status.OK;
      return ImmutableList.of(submitRecord);
    }

    submitRecord.status = Status.NOT_READY;
    submitRecord.requirements = ImmutableList.of(DEFAULT_SUBMIT_REQUIREMENT_FOR_CHECKS);
    return ImmutableSet.of(submitRecord);
  }

  private static CombinedCheckState getCombinedCheckState(
      ImmutableMap<String, CheckInfo> checks,
      ImmutableMap<String, Checker> appliedCheckers,
      Change.Id changeId) {
    ImmutableListMultimap.Builder<CheckState, Boolean> statesAndRequired =
        ImmutableListMultimap.builder();
    for (Map.Entry<String, Checker> entry : appliedCheckers.entrySet()) {
      String checkerUuid = entry.getKey();
      Checker checker = entry.getValue();

      if (checker.getStatus() == CheckerStatus.DISABLED) {
        // Disabled checkers doesn't block submission.
        continue;
      }

      ImmutableList<BlockingCondition> blockingConditions =
          checker.getBlockingConditions().asList();
      if (blockingConditions.isEmpty()) {
        // The checker is not blocking if no blocking condition is set.
        continue;
      }
      if (blockingConditions.size() > 1
          || blockingConditions.get(0) != BlockingCondition.STATE_NOT_PASSING) {
        // When a new blocking condition is introduced, this submit rule needs to be adjusted to
        // respect that.
        String errorMessage = String.format("illegal blocking conditions %s", blockingConditions);
        throw new IllegalStateException(errorMessage);
      }

      if (!checks.keySet().contains(checkerUuid)) {
        // ListChecks is expected to return checks for all applicable checkers. If not, there is
        // an inconsistency.
        String errorMessage =
            String.format(
                "missing check results for checker %s on change %s", checkerUuid, changeId);
        throw new IllegalStateException(errorMessage);
      }

      statesAndRequired.put(checks.get(checkerUuid).state, true);
    }

    return CombinedCheckState.combine(statesAndRequired.build());
  }

  private static Collection<SubmitRecord> singletonRecordForRuleError(String reason) {
    SubmitRecord submitRecord = new SubmitRecord();
    submitRecord.errorMessage = reason;
    submitRecord.status = SubmitRecord.Status.RULE_ERROR;
    return ImmutableList.of(submitRecord);
  }
}
