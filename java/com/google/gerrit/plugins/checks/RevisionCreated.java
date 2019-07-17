package com.google.gerrit.plugins.checks;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.extensions.events.RevisionCreatedListener;
import com.google.gerrit.plugins.checks.Checks.GetCheckOptions;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.UserInitiated;
import java.io.IOException;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class RevisionCreated implements RevisionCreatedListener {

  private final Checks checks;
  private final Checkers checkers;
  private final Provider<ChecksUpdate> checksUpdate;
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Inject
  RevisionCreated(
      Checks checks, Checkers checkers, @UserInitiated Provider<ChecksUpdate> checksUpdate) {
    this.checks = checks;
    this.checkers = checkers;
    this.checksUpdate = checksUpdate;
  }

  @Override
  public void onRevisionCreated(Event event) {
    ChangeInfo changeInfo = event.getChange();
    RevisionInfo revisionInfo = event.getRevision();
    Change.Id changeId = Change.id(changeInfo._number);
    PatchSet.Id patchId = PatchSet.id(changeId, revisionInfo._number);
    PatchSet.Id previousPatchId = PatchSet.id(changeId, revisionInfo._number - 1);
    try {
      ImmutableList<Check> previousCheckList =
          checks.getChecks(
              Project.nameKey(changeInfo.project), previousPatchId, GetCheckOptions.defaults());
      for (Check check : previousCheckList) {
        CheckerUuid checkerUuid = check.key().checkerUuid();
        Checker checker = checkers.getChecker(checkerUuid).get();
        if (checker.getCopyPolicy().contains(revisionInfo.kind)
            && check.state().isInProgress() == false) {
          CheckKey key =
              CheckKey.create(Project.NameKey.parse(changeInfo.project), patchId, checkerUuid);
          CheckUpdate checkUpdate =
              CheckUpdate.builder()
                  .setMessage(check.message().get())
                  .setState(check.state())
                  .setUrl(check.url().get())
                  .build();
          checksUpdate.get().createCheck(key, checkUpdate);
        }
      }
    } catch (IOException iox) {
      logger.atSevere().withCause(iox).log("Error reading from database %s", iox.getMessage());
    } catch (ConfigInvalidException cix) {
      logger.atSevere().withCause(cix).log("Invalid checker %s", cix.getMessage());
    }
  }
}
