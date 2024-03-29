package com.amazonaws.gurureviewercli.adapter;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

import lombok.val;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;

import com.amazonaws.gurureviewercli.exceptions.GuruCliException;
import com.amazonaws.gurureviewercli.model.Configuration;
import com.amazonaws.gurureviewercli.model.ErrorCodes;
import com.amazonaws.gurureviewercli.model.GitMetaData;
import com.amazonaws.gurureviewercli.util.Log;

/**
 * Util to sanity-check if a repo is a valid git repository that can be analyzed by CodeGuru.
 */
public final class GitAdapter {

    private static final String GITHUB_UNKNOWN_COMMIT = "0000000000000000000000000000000000000000";
    // this is the sha for an empty commit, so any diff against this will return the full repo content.
    private static final String GITHUB_EMPTY_COMMIT_SHA = "4b825dc642cb6eb9a060e54bf8d69288fbee4904";

    @Nonnull
    public static GitMetaData getGitMetaData(final Configuration config, final Path pathToRepo) throws IOException {
        val gitDir = pathToRepo.toRealPath().resolve(".git");
        if (!gitDir.toFile().isDirectory()) {
            // if the directory is not under version control, return a dummy object.
            return GitMetaData.builder()
                              .repoRoot(pathToRepo)
                              .userName("nobody")
                              .currentBranch("unknown")
                              .build();
        }
        return tryGetMetaData(config, pathToRepo.toRealPath().resolve(".git"));
    }

    @Nonnull
    protected static GitMetaData tryGetMetaData(final Configuration config, final Path gitDir) {
        if (!gitDir.toFile().isDirectory()) {
            throw new GuruCliException(ErrorCodes.GIT_INVALID_DIR);
        }
        val builder = new FileRepositoryBuilder();
        try (val repository = builder.setGitDir(gitDir.toFile()).findGitDir().build()) {
            val userName = repository.getConfig().getString("user", null, "email");
            val urlString = repository.getConfig().getString("remote", "origin", "url");
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

            metadata.setVersionedFiles(getChangedFiles(repository));
            config.setVersionedFiles(metadata.getVersionedFiles());
            if (config.getBeforeCommit() == null || config.getAfterCommit() == null) {
                // ask if commits should be inferred or if the entire repo should be scanned.
                Log.warn("CodeGuru will perform a full repository analysis if you do not provide a commit range.");
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

            validateCommits(config, repository);
            metadata.setBeforeCommit(config.getBeforeCommit());
            metadata.setAfterCommit(config.getAfterCommit());
            return metadata;

        } catch (IOException | GitAPIException e) {
            throw new GuruCliException(ErrorCodes.GIT_INVALID_DIR, "Cannot read " + gitDir, e);
        }
    }

    private static Collection<Path> getChangedFiles(final Repository repository) throws IOException {
        val headCommitId = repository.resolve(Constants.HEAD);
        if (headCommitId == null) {
            return Collections.emptySet();
        }
        val rootDir = repository.getWorkTree().toPath();
        RevWalk revWalk = new RevWalk(repository);
        RevCommit commit = revWalk.parseCommit(headCommitId);
        val treeWalk = new TreeWalk(repository);
        treeWalk.addTree(commit.getTree());
        treeWalk.setRecursive(false);
        val allFiles = new HashSet<Path>();
        while (treeWalk.next()) {
            if (treeWalk.isSubtree()) {
                treeWalk.enterSubtree();
            } else {
                val normalizedFile = rootDir.resolve(treeWalk.getPathString()).toFile().getCanonicalFile();
                if (normalizedFile.isFile()) {
                    allFiles.add(normalizedFile.toPath());
                }
            }
        }
        return allFiles;
    }

    private static boolean validateCommits(final Configuration config, final Repository repo)
        throws GitAPIException {
        String beforeCommitSha = config.getBeforeCommit();
        if (GITHUB_UNKNOWN_COMMIT.equals(config.getBeforeCommit())) {
            beforeCommitSha = GITHUB_EMPTY_COMMIT_SHA;
        }

        val beforeTreeIter = treeForCommitId(repo, beforeCommitSha);
        val afterTreeIter = treeForCommitId(repo, config.getAfterCommit());

        // Resolve git constants, such as HEAD^^ to the actual commit hash
        config.setBeforeCommit(resolveSha(repo, beforeCommitSha));
        config.setAfterCommit(resolveSha(repo, config.getAfterCommit()));

        val diffEntries = new Git(repo).diff().setOldTree(beforeTreeIter).setNewTree(afterTreeIter).call();
        if (diffEntries.isEmpty()) {
            throw new GuruCliException(ErrorCodes.GIT_EMPTY_DIFF, String.format("No difference between {} and {}",
                                                                                beforeTreeIter, afterTreeIter));
        }
        return true;
    }

    private static String resolveSha(final Repository repo, final String commitName) {
        try {
            return repo.resolve(commitName).getName();
        } catch (Throwable e) {
            throw new GuruCliException(ErrorCodes.GIT_INVALID_COMMITS, "Invalid commit " + commitName);
        }
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

