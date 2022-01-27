package com.amazonaws.gurureviewercli.model;

import javax.annotation.Nullable;
import java.nio.file.Path;

import lombok.Builder;
import lombok.Data;

/**
 * Metadata collected about the analyzed git repo.
 */
@Builder
@Data
public class GitMetaData {

    private Path repoRoot;

    private String userName;

    private String currentBranch;

    @Builder.Default
    private String pullRequestId = "0";

    private @Nullable String remoteUrl;

    private @Nullable String beforeCommit;

    private @Nullable String afterCommit;

}
