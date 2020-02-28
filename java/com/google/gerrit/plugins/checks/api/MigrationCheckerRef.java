package com.google.gerrit.plugins.checks.api;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.plugins.checks.CheckerRef;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.RepoContext;
import com.google.gerrit.server.update.RepoOnlyOp;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefRename;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;

@Singleton
public class MigrationCheckerRef {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final GitRepositoryManager repoManager;
  private final AllProjectsName allProjectsName;
  private final BatchUpdate.Factory updateFactory;
  private final Provider<CurrentUser> user;

  @Inject
  MigrationCheckerRef(
      GitRepositoryManager repoManager,
      AllProjectsName allProjectsName,
      BatchUpdate.Factory updateFactory,
      Provider<CurrentUser> user) {
    this.repoManager = repoManager;
    this.allProjectsName = allProjectsName;
    this.updateFactory = updateFactory;
    this.user = user;
  }

  public void migrate() {
    String tempRef = "refs/temp";
    try (Repository repo = repoManager.openRepository(allProjectsName)) {
      Ref ref = repo.exactRef(CheckerRef.LEGACY_REFS_META_CHECKERS);
      if (ref == null) {
        return;
      }
      renameRef(CheckerRef.LEGACY_REFS_META_CHECKERS, tempRef, repo);
      renameRef(tempRef, CheckerRef.REFS_META_CHECKERS, repo);
    } catch (Exception ex) {
      // ignore, if failed for any reason it shouldn't stop the REST endpoint from working.
      logger.atSevere().withCause(ex).log(
          "Failed to migrate refs/meta/checkers/ to refs/meta/checkers (without trailing slash");
    }
  }

  private void renameRef(String currentName, String targetName, Repository repo)
      throws IOException, UpdateException, RestApiException {
    RefRename refRename = repo.renameRef(currentName, targetName);
    Result result = refRename.rename();
    if (result != Result.RENAMED) {
      throw new ResourceConflictException(
          String.format(
              "Rename of %s to %s failed with the failure: %s",
              currentName, targetName, result.name()));
    }
    try (BatchUpdate bu =
        updateFactory.create(
            Project.nameKey(allProjectsName.get()), user.get(), TimeUtil.nowTs())) {
      bu.addRepoOnlyOp(new DeleteRefOp(currentName));
      bu.execute();
    }
  }
}

class DeleteRefOp implements RepoOnlyOp {
  private final String toDelete;

  DeleteRefOp(String toDelete) {
    this.toDelete = toDelete;
  }

  @Override
  public void updateRepo(RepoContext ctx) throws IOException {
    Optional<ObjectId> legacyRef = ctx.getRepoView().getRef(toDelete);
    ctx.addRefUpdate(legacyRef.get(), ObjectId.zeroId(), toDelete);
  }
}
