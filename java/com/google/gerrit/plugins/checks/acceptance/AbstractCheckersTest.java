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

package com.google.gerrit.plugins.checks.acceptance;

import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowCapability;

import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.ProjectResetter;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.plugins.checks.CheckerRef;
import com.google.gerrit.plugins.checks.acceptance.testsuite.CheckOperations;
import com.google.gerrit.plugins.checks.acceptance.testsuite.CheckerOperations;
import com.google.gerrit.plugins.checks.api.Checkers;
import com.google.gerrit.plugins.checks.api.ChecksFactory;
import com.google.gerrit.plugins.checks.api.PendingChecks;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllUsersName;
import com.google.inject.Inject;
import org.junit.Before;

// TODO(dborowitz): Improve the plugin test framework so we can avoid subclassing:
//  * Defer injection until after the plugin is loaded, so we can @Inject members defined in plugin
//    modules, rather than hard-coding them non-scalably like we do here.
//  * Don't require all test classes to hard-code the @TestPlugin annotation.
@TestPlugin(
    name = "checks",
    sysModule = "com.google.gerrit.plugins.checks.acceptance.TestModule",
    httpModule = "com.google.gerrit.plugins.checks.HttpModule")
public class AbstractCheckersTest extends LightweightPluginDaemonTest {
  @Inject private ProjectOperations projectOperations;

  protected CheckerOperations checkerOperations;
  protected CheckOperations checkOperations;
  protected Checkers checkersApi;
  protected ChecksFactory checksApiFactory;
  protected PendingChecks pendingChecksApi;

  @Override
  protected ProjectResetter.Config resetProjects(
      AllProjectsName allProjects, AllUsersName allUsers) {
    return super.resetProjects(allProjects, allUsers)
        .toBuilder()
        .reset(allProjects, CheckerRef.REFS_CHECKERS + "*", CheckerRef.REFS_META_CHECKERS)
        .build();
  }

  @Before
  public void setUpCheckersPlugin() throws Exception {
    checkerOperations = plugin.getSysInjector().getInstance(CheckerOperations.class);
    checkOperations = plugin.getSysInjector().getInstance(CheckOperations.class);
    checkersApi = plugin.getHttpInjector().getInstance(Checkers.class);
    checksApiFactory = plugin.getHttpInjector().getInstance(ChecksFactory.class);
    pendingChecksApi = plugin.getHttpInjector().getInstance(PendingChecks.class);

    projectOperations
        .project(allProjects)
        .forUpdate()
        .add(
            allowCapability("checks-administrateCheckers")
                .group(group("Administrators").getGroupUUID()))
        .update();
  }
}
