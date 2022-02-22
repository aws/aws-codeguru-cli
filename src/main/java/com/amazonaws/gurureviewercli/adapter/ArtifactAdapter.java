package com.amazonaws.gurureviewercli.adapter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import lombok.val;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import com.amazonaws.gurureviewercli.model.Configuration;
import com.amazonaws.gurureviewercli.model.ScanMetaData;
import com.amazonaws.gurureviewercli.util.ZipUtils;

/**
 * Utility class class to Zip and upload source and build artifacts to S3.
 */
public final class ArtifactAdapter {

    /**
     * Zip and upload source and build artifacts to S3.
     *
     * @param config        The current {@link Configuration}
     * @param tempDir       A temp directory where files can be copied to and zipped. Will be deleted after completion.
     * @param repositoryDir The root directory of the repo to analyze
     * @param sourceDirs    The list of source directories under repositoryDir.
     * @param buildDirs     The list of build directories (can be empty).
     * @param bucketName    The name of the S3 bucket that should be used for the upload.
     * @return Metadata about what was zipped and uploaded.
     * @throws IOException If writing to tempDir fails.
     */
    public static ScanMetaData zipAndUpload(final Configuration config,
                                            final Path tempDir,
                                            final Path repositoryDir,
                                            final List<Path> sourceDirs,
                                            final List<Path> buildDirs,
                                            final String bucketName) throws IOException {
        try {
            boolean scanVersionedFilesOnly = false;
            if (config.getVersionedFiles() != null && !config.getVersionedFiles().isEmpty()) {
                scanVersionedFilesOnly =
                    !config.isInteractiveMode() ||
                    config.getTextIO()
                          .newBooleanInputReader()
                          .withTrueInput("y")
                          .withFalseInput("n")
                          .read("Only analyze files under version control?");
            }
            final String sourceKey;
            if (scanVersionedFilesOnly) {
                val filesToScan = new ArrayList<Path>(config.getVersionedFiles());

                filesToScan.addAll(ZipUtils.getFilesInDirectory(repositoryDir.resolve(".git")));
                sourceKey = "";
                throw new RuntimeException("Not implemented");
            } else {
                val sourceDirsAndGit = new ArrayList<Path>(sourceDirs);
                if (config.getBeforeCommit() != null && config.getAfterCommit() != null) {
                    // only add the git folder if a commit range is provided.
                    sourceDirsAndGit.add(repositoryDir.resolve(".git").toAbsolutePath());
                }
                sourceKey = zipAndUploadDir("analysis-src-" + UUID.randomUUID(), sourceDirsAndGit, repositoryDir,
                                            bucketName, tempDir, config.getAccountId(), config.getS3Client());
            }
            final String buildKey;
            if (buildDirs != null && !buildDirs.isEmpty()) {
                for (val buildDir : buildDirs) {
                    if (!buildDir.toFile().isDirectory()) {
                        throw new FileNotFoundException("Provided build directory not found " + buildDir);
                    }
                }
                buildKey =
                    zipAndUploadDir("analysis-bin-" + UUID.randomUUID(), buildDirs,
                                    bucketName, tempDir, config.getAccountId(), config.getS3Client());
            } else {
                buildKey = null;
            }
            return ScanMetaData.builder()
                               .bucketName(bucketName)
                               .repositoryRoot(repositoryDir)
                               .sourceDirectories(sourceDirs)
                               .sourceKey(sourceKey)
                               .buildKey(buildKey)
                               .build();
        } finally {
            // Delete the temp dir.
            try (val walker = Files.walk(tempDir)) {
                walker.sorted(Comparator.reverseOrder())
                      .map(Path::toFile)
                      .forEach(File::delete);
            }
        }
    }


    private static String zipAndUploadDir(final String artifactName,
                                          final List<Path> dirNames,
                                          final String bucketName,
                                          final Path tempDir,
                                          final String accountId,
                                          final S3Client s3Client) throws IOException {
        return zipAndUploadDir(artifactName, dirNames, null, bucketName, tempDir, accountId, s3Client);
    }

    private static String zipAndUploadDir(final String artifactName,
                                          final List<Path> dirNames,
                                          final Path rootDir,
                                          final String bucketName,
                                          final Path tempDir,
                                          final String accountId,
                                          final S3Client s3Client) throws IOException {
        if (dirNames != null) {
            val zipFileName = artifactName + ".zip";
            val zipFile = tempDir.resolve(zipFileName).toAbsolutePath();
            val s3Key = zipFileName;
            if (!zipFile.toFile().isFile()) {
                if (rootDir != null) {
                    ZipUtils.pack(dirNames, rootDir, zipFile.toString());
                } else {
                    ZipUtils.pack(dirNames, zipFile.toString());
                }
            }
            val putObjectRequest = PutObjectRequest.builder()
                                                   .bucket(bucketName)
                                                   .key(s3Key)
                                                   .expectedBucketOwner(accountId)
                                                   .build();
            s3Client.putObject(putObjectRequest, zipFile);
            return s3Key;
        }
        return null;
    }


    private ArtifactAdapter() {
        // do not instantiate
    }
}
