package com.amazonaws.gurureviewercli.adapter;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Path;

import com.google.common.annotations.VisibleForTesting;
import lombok.val;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;

import com.amazonaws.gurureviewercli.exceptions.GuruCliException;
import com.amazonaws.gurureviewercli.model.Configuration;
import com.amazonaws.gurureviewercli.model.ErrorCodes;
import com.amazonaws.gurureviewercli.model.GitMetaData;
import com.amazonaws.gurureviewercli.util.Log;

/**
 * Util to sanity-check if a repo is a valid git repository that can be analyzed by CodeGuru.
 */
public final class GitAdapter {

    @Nullable
    public static GitMetaData getGitMetatData(final Configuration config, final Path pathToRepo) {
        return tryGetMetaData(config, pathToRepo.toAbsolutePath().normalize().resolve(".git"));
    }

    @VisibleForTesting
    @Nullable
    protected static GitMetaData tryGetMetaData(final Configuration config, final Path gitDir) {
        if (!gitDir.toFile().isDirectory()) {
            throw new GuruCliException(ErrorCodes.GIT_INVALID_DIR);
        }
        val builder = new FileRepositoryBuilder();
        try (val repository = builder.setGitDir(gitDir.toFile()).findGitDir().build()) {
            val userName = repository.getConfig().getString("user", null, "email");
            val urlString = repository.getConfig().getString("remote", "origin", "url");
            if (urlString == null) {
                throw new GuruCliException(ErrorCodes.GIT_REMOTE_MISSING);
            }
            val branchName = repository.getBranch();
            if (branchName == null) {
                throw new GuruCliException(ErrorCodes.GIT_BRANCH_MISSING);
            }

            val metadata = GitMetaData.builder()
                                      .currentBranch(branchName)
                                      .userName(userName)
                                      .repoRoot(gitDir.getParent())
                                      .remoteUrl(urlString)
                                      .build();

            if (config.getBeforeCommit() == null || config.getAfterCommit() == null) {
                // ask if commits should be inferred or if the entire repo should be scanned.
                Log.warn("CodeGuru will preform a full repository analysis if you do not provide a commit range.");
                Log.warn("For pricing details see: https://aws.amazon.com/codeguru/pricing/");
                val doPackageScan =
                    !config.isInteractiveMode() ||
                    config.getTextIO()
                          .newBooleanInputReader()
                          .withTrueInput("y")
                          .withFalseInput("n")
                          .read("Do you want to perform a full repository analysis?");
                if (doPackageScan) {
                    return metadata;
                } else {
                    throw new GuruCliException(ErrorCodes.USER_ABORT, "Use --commit-range to set a commit range");
                }
            }

            if (validateCommits(config, repository)) {
                metadata.setBeforeCommit(config.getBeforeCommit());
                metadata.setAfterCommit(config.getAfterCommit());
            } else {
                throw new GuruCliException(ErrorCodes.GIT_INVALID_COMMITS);
            }

            return metadata;

        } catch (IOException | GitAPIException e) {
            throw new GuruCliException(ErrorCodes.GIT_INVALID_DIR, "Cannot read " + gitDir, e);
        }
    }

    private static boolean validateCommits(final Configuration config, final Repository repo)
        throws GitAPIException {

        val beforeTreeIter = treeForCommitId(repo, config.getBeforeCommit());
        val afterTreeIter = treeForCommitId(repo, config.getAfterCommit());

        try {
            // Resolve git constants, such as HEAD^^ to the actual commit hash
            config.setBeforeCommit(repo.resolve(config.getBeforeCommit()).getName());
            config.setAfterCommit(repo.resolve(config.getAfterCommit()).getName());
        } catch (Throwable e) {
            throw new GuruCliException(ErrorCodes.GIT_INVALID_COMMITS);
        }
        val diffEntries = new Git(repo).diff().setOldTree(beforeTreeIter).setNewTree(afterTreeIter).call();
        if (diffEntries.isEmpty()) {
            throw new GuruCliException(ErrorCodes.GIT_EMPTY_DIFF);
        }

        return true;
    }

    private static CanonicalTreeParser treeForCommitId(final Repository repo, final String commitId) {
        try (RevWalk walk = new RevWalk(repo)) {
            val commit = walk.parseCommit(repo.resolve(commitId));
            val treeId = commit.getTree().getId();
            try (ObjectReader reader = repo.newObjectReader()) {
                return new CanonicalTreeParser(null, reader, treeId);
            }
        } catch (NullPointerException e) {
            throw new GuruCliException(ErrorCodes.GIT_INVALID_COMMITS, "Not a valid commit id " + commitId, e);
        } catch (IOException e) {
            throw new GuruCliException(ErrorCodes.GIT_INVALID_COMMITS, "Cannot parse commit id " + commitId, e);
        }
    }

    private GitAdapter() {
        // do not instantiate
    }
}

