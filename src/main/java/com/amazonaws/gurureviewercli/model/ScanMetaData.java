package com.amazonaws.gurureviewercli.model;

import java.nio.file.Path;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data class to store information about a started CodeGuru Review.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScanMetaData {

    private String associationArn;

    private String codeReviewArn;

    private String region;

    private Path repositoryRoot;

    private List<Path> sourceDirectories;

    private String bucketName;

    private String sourceKey;

    private String buildKey;
}
