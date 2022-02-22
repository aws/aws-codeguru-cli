package com.amazonaws.gurureviewercli.adapter;

import java.nio.file.Path;
import java.nio.file.Paths;

import lombok.val;
import org.beryx.textio.TextIO;
import org.beryx.textio.mock.MockTextTerminal;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.amazonaws.gurureviewercli.exceptions.GuruCliException;
import com.amazonaws.gurureviewercli.model.Configuration;
import com.amazonaws.gurureviewercli.model.ErrorCodes;

class GitAdapterTest {

    private static final Path RESOURCE_ROOT = Paths.get("test-data");

    @Test
    public void test_getGitMetaData_notARepo() {
        val repo = RESOURCE_ROOT.resolve("fresh-repo-without-remote");
        GuruCliException ret = Assertions.assertThrows(GuruCliException.class, () ->
            GitAdapter.tryGetMetaData(configWithoutCommits(repo), repo.resolve("notgit")));
        Assertions.assertEquals(ErrorCodes.GIT_INVALID_DIR, ret.getErrorCode());
    }

    @Test
    public void test_getGitMetaData_noRemote() throws Exception {
        val repo = RESOURCE_ROOT.resolve("fresh-repo-no-remote");
        val metadata = GitAdapter.tryGetMetaData(configWithoutCommits(repo), repo.resolve("git"));
        Assertions.assertNull(metadata.getRemoteUrl());
        Assertions.assertNotNull(metadata.getCurrentBranch());
        Assertions.assertEquals(repo, metadata.getRepoRoot());
    }


    @Test
    public void test_getGitMetaData_oneCommit_packageScan() {
        val repo = RESOURCE_ROOT.resolve("one-commit");
        val mockTerminal = new MockTextTerminal();
        mockTerminal.getInputs().add("y");
        val config = Configuration.builder()
                                  .textIO(new TextIO(mockTerminal))
                                  .interactiveMode(true)
                                  .build();
        val gitMetaData = GitAdapter.tryGetMetaData(config, repo.resolve("git"));
        Assertions.assertNotNull(gitMetaData);
        Assertions.assertNull(gitMetaData.getBeforeCommit());
        Assertions.assertNull(gitMetaData.getAfterCommit());
        Assertions.assertEquals(1, gitMetaData.getVersionedFiles().size());
        Assertions.assertEquals("master", gitMetaData.getCurrentBranch());
        Assertions.assertEquals("git@amazon.com:username/new_repo", gitMetaData.getRemoteUrl());
    }

    @Test
    public void test_getGitMetaData_oneCommit_packageScanAbort() {
        val repo = RESOURCE_ROOT.resolve("one-commit");
        val mockTerminal = new MockTextTerminal();
        mockTerminal.getInputs().add("n");
        val config = Configuration.builder()
                                .textIO(new TextIO(mockTerminal))
                                .interactiveMode(true)
                                .build();
        GuruCliException ret = Assertions.assertThrows(GuruCliException.class, () ->
            GitAdapter.tryGetMetaData(config, repo.resolve("git")));
        Assertions.assertEquals(ErrorCodes.USER_ABORT, ret.getErrorCode());

    }

    @Test
    public void test_getGitMetaData_twoCommits_validCommits() {
        val repo = RESOURCE_ROOT.resolve("two-commits");
        val config = configWithoutCommits(repo);
        config.setBeforeCommit("cdb0fcad7400610b1d1797a326a89414525160fe");
        config.setAfterCommit("8ece465b7ecf8337bf767c9602d21bb92f2fad8a");
        val gitMetaData = GitAdapter.tryGetMetaData(config, repo.resolve("git"));
        Assertions.assertNotNull(gitMetaData);
        Assertions.assertNotNull(gitMetaData.getBeforeCommit());
        Assertions.assertNotNull(gitMetaData.getAfterCommit());
        Assertions.assertEquals(1, gitMetaData.getVersionedFiles().size());
        Assertions.assertEquals("master", gitMetaData.getCurrentBranch());
        Assertions.assertEquals("git@amazon.com:username/new_repo", gitMetaData.getRemoteUrl());
    }

    @Test
    public void test_getGitMetaData_twoCommits_commitShortHand() {
        val repo = RESOURCE_ROOT.resolve("two-commits");
        val config = configWithoutCommits(repo);
        config.setBeforeCommit("HEAD^");
        config.setAfterCommit("HEAD");
        val gitMetaData = GitAdapter.tryGetMetaData(config, repo.resolve("git"));
        Assertions.assertNotNull(gitMetaData);
        Assertions.assertNotNull(gitMetaData.getBeforeCommit());
        Assertions.assertNotNull(gitMetaData.getAfterCommit());
        Assertions.assertEquals("master", gitMetaData.getCurrentBranch());
        Assertions.assertEquals("git@amazon.com:username/new_repo", gitMetaData.getRemoteUrl());
    }

    @Test
    public void test_getGitMetaData_twoCommits_invalidCommits() {
        val repo = RESOURCE_ROOT.resolve("two-commits");
        val config = configWithoutCommits(repo);
        config.setBeforeCommit("thisIsNotACommitHash");
        config.setAfterCommit("8ece465b7ecf8337bf767c9602d21bb92f2fad8a");

        Exception ret = Assertions.assertThrows(Exception.class, () ->
            GitAdapter.tryGetMetaData(config, repo.resolve("git")));
        Assertions.assertTrue(ret.getMessage().contains("Not a valid commit id "));
    }

    private Configuration configWithoutCommits(final Path workingDir) {
        return Configuration.builder()
                            .textIO(new TextIO(new MockTextTerminal()))
                            .interactiveMode(false)
                            .build();
    }
}