package com.amazonaws.gurureviewercli.model;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.Collection;

import lombok.Builder;
import lombok.Data;
import org.beryx.textio.TextIO;
import software.amazon.awssdk.services.codegurureviewer.CodeGuruReviewerClient;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Class to hold all shared configuration data. This object is mutable and information is added as it becomes
 * available.
 */
@Data
@Builder
public class Configuration {

    private boolean interactiveMode;

    private CodeGuruReviewerClient guruFrontendService;

    private S3Client s3Client;

    private String accountId;

    private String region;

    private String repoName;

    private String keyId;

    private Path rootDir;

    private TextIO textIO;

    private String bucketName;

    private @Nullable
    String beforeCommit;

    private @Nullable
    String afterCommit;

    private @Nullable
    Collection<Path> versionedFiles;
}
