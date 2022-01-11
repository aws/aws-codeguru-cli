package com.amazonaws.gurureviewercli.model;

import javax.annotation.Nullable;
import java.nio.file.Path;

import lombok.Builder;
import lombok.Data;
import org.beryx.textio.TextIO;

import com.amazonaws.services.codegurureviewer.AmazonCodeGuruReviewer;
import com.amazonaws.services.s3.AmazonS3;

/**
 * Class to hold all shared configuration data. This object is mutable and information is added as it becomes
 * available.
 */
@Data
@Builder
public class Configuration {

    private boolean interactiveMode;

    private AmazonCodeGuruReviewer guruFrontendService;

    private AmazonS3 s3Client;

    private String accountId;

    private String region;

    private String repoName;

    private Path rootDir;

    private TextIO textIO;

    private String bucketName;

    private @Nullable
    String beforeCommit;

    private @Nullable
    String afterCommit;
}
