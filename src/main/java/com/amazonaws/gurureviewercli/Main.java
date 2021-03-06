package com.amazonaws.gurureviewercli;


import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import lombok.val;
import org.beryx.textio.TextIO;
import org.beryx.textio.system.SystemTextTerminal;
import org.eclipse.jgit.util.FileUtils;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.codegurureviewer.CodeGuruReviewerClient;
import software.amazon.awssdk.services.codegurureviewer.model.RecommendationSummary;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.sts.StsClient;

import com.amazonaws.gurureviewercli.adapter.GitAdapter;
import com.amazonaws.gurureviewercli.adapter.ResultsAdapter;
import com.amazonaws.gurureviewercli.adapter.ScanAdapter;
import com.amazonaws.gurureviewercli.exceptions.GuruCliException;
import com.amazonaws.gurureviewercli.model.Configuration;
import com.amazonaws.gurureviewercli.model.ErrorCodes;
import com.amazonaws.gurureviewercli.model.GitMetaData;
import com.amazonaws.gurureviewercli.model.ScanMetaData;
import com.amazonaws.gurureviewercli.model.configfile.CustomConfiguration;
import com.amazonaws.gurureviewercli.util.CodeInsightExport;
import com.amazonaws.gurureviewercli.util.Log;
import com.amazonaws.gurureviewercli.util.RecommendationPrinter;
import com.amazonaws.gurureviewercli.util.RecommendationsFilter;

public class Main {
    private static final String REVIEWER_ENDPOINT_PATTERN = "https://codeguru-reviewer.%s.amazonaws.com";

    @Parameter(names = {"--region"},
               description = "Region where CodeGuru Reviewer will run.",
               required = false)
    private String regionName = "us-east-1";

    @Parameter(names = {"--profile"},
               description = "Use a named profile to get AWS Credentials",
               required = false)
    private String profileName;

    @Parameter(names = {"--commit-range", "-c"},
               description = "Range of commits to analyze separated by ':'. For example HEAD^:HEAD ",
               required = false)
    private String commitRange;

    @Parameter(names = {"--no-prompt"},
               description = "Run in non-interactive mode.",
               required = false)
    private boolean noPrompt;

    @Parameter(names = {"--fail-on-recommendations"},
               description = "Return error code 5 if CodeGuru reports recommendations.",
               required = false)
    private boolean failOnRecommendations;

    @Parameter(names = {"--bitbucket-code-insights"},
               description = "Output directory for Bitbucket insights report and annotation files.",
               required = false)
    private String bitbucketCodeInsightsDirectory;
    @Parameter(names = {"--root-dir", "-r"},
               description = "The root directory of the project that should be analyzed.",
               required = true)
    private String repoDir;

    @Parameter(names = {"--src", "-s"},
               description = "Source directories to be analyzed. Can be used multiple times.")
    private List<String> sourceDirs;

    @Parameter(names = {"--build", "-b"},
               description = "Directory of all build artifacts. Can be used multiple times.")
    private List<String> buildDirs;

    @Parameter(names = {"--output", "-o"},
               description = "Output directory.")
    private String outputDir = "./code-guru";

    @Parameter(names = {"--bucket-name"},
               description = "Name of S3 bucket that source and build artifacts will be uploaded to for analysis."
                             + " The bucket name has to be prefixed with 'codeguru-reviewer-'. If no bucket name"
                             + " is provided, the CLI will create a bucket automatically.")
    private String bucketName;

    @Parameter(names = {"--kms-key-id", "-kms"},
               description = "KMS Key ID to encrypt source and build artifacts in S3")
    private String kmsKeyId;

    public static void main(String[] argv) {
        val textIO = new TextIO(new SystemTextTerminal());

        val main = new Main();
        val jCommander = JCommander.newBuilder()
                                   .addObject(main)
                                   .build();
        if (argv.length == 0) {
            jCommander.usage();
            return;
        }
        try {
            jCommander.parse(argv);
            val config = Configuration.builder()
                                      .textIO(textIO)
                                      .interactiveMode(!main.noPrompt)
                                      .bucketName(main.bucketName)
                                      .build();
            main.validateInitialConfig(config);
            // try to build the AWS client objects first.
            main.createAWSClients(config);

            String repoName = config.getRootDir().toFile().getName();
            config.setRepoName(repoName);

            // check if repo is valid git.
            val gitMetaData = main.readGitMetaData(config, Paths.get(main.repoDir).toRealPath());

            ScanMetaData scanMetaData = null;
            List<RecommendationSummary> results = new ArrayList<>();
            try {
                val sourcePaths = main.sourceDirs.stream()
                                                 .map(Paths::get).map(Path::toAbsolutePath).map(Path::normalize)
                                                 .collect(Collectors.toList());
                List<Path> buildPaths = null;
                if (main.buildDirs != null) {
                    buildPaths = main.buildDirs.stream()
                                               .map(Paths::get).map(Path::toAbsolutePath).map(Path::normalize)
                                               .collect(Collectors.toList());
                }                scanMetaData = ScanAdapter.startScan(config, gitMetaData, sourcePaths, buildPaths);
                results.addAll(ScanAdapter.fetchResults(config, scanMetaData));
            } finally {
                if (scanMetaData != null) {
                    // try to clean up objects from S3.
                    main.tryDeleteS3Object(config.getS3Client(),
                                           scanMetaData.getBucketName(),
                                           scanMetaData.getSourceKey());
                    main.tryDeleteS3Object(config.getS3Client(),
                                           scanMetaData.getBucketName(),
                                           scanMetaData.getBuildKey());
                }
            }

            val customConfigFile = config.getRootDir().resolve(".codeguru-ignore.yml");
            if (customConfigFile.toFile().isFile()) {
                Log.info("Using customer provided config: " + customConfigFile.toAbsolutePath());
                int originalResultsCount = results.size();
                results = RecommendationsFilter.filterRecommendations(results,
                                                                      CustomConfiguration.load(customConfigFile));
                Log.info("%d recommendations were suppressed.", originalResultsCount - results.size());
            }

            val outputPath = Paths.get(main.outputDir);
            if (!outputPath.toFile().exists()) {
                if (!outputPath.toFile().mkdirs()) {
                    Log.error("Failed to create output directory %s.", outputPath);
                }
            }
            ResultsAdapter.saveResults(outputPath, results, scanMetaData);
            Log.info("Analysis finished.");

            if (main.bitbucketCodeInsightsDirectory != null) {
                val bitBucketDir = new File(main.bitbucketCodeInsightsDirectory).getCanonicalFile();
                FileUtils.mkdirs(bitBucketDir, true);
                CodeInsightExport.report(results, scanMetaData, bitBucketDir.toPath());
            }

            if (main.failOnRecommendations && !results.isEmpty()) {
                RecommendationPrinter.print(results);
                Log.error("Exiting with code 5 because %d recommendations were found and --fail-on-recommendations"
                         + " is used.", results.size());
                System.exit(5);
            }
        } catch (GuruCliException e) {
            Log.error("%s: %s", e.getErrorCode(), e.getMessage());
            e.printStackTrace();
            System.exit(3);
        } catch (ParameterException e) {
            Log.error(e);
            jCommander.usage();
            System.exit(1);
        } catch (Exception e) {
            e.printStackTrace();
            Log.error(e);
            System.exit(2);
        }
        System.exit(0);
    }

    protected GitMetaData readGitMetaData(final Configuration config, final Path repoRoot) throws IOException {
        if (commitRange != null) {
            val commits = commitRange.split(":");
            if (commits.length != 2) {
                throw new GuruCliException(ErrorCodes.GIT_INVALID_COMMITS,
                                           "Invalid value for --commit-range. Use '[before commit]:[after commit]'.");
            }
            config.setBeforeCommit(commits[0]);
            config.setAfterCommit(commits[1]);
        }

        return GitAdapter.getGitMetaData(config, repoRoot);
    }

    private void validateInitialConfig(final Configuration config) throws IOException {
        if (config.getBucketName() != null && !config.getBucketName().startsWith("codeguru-reviewer-")) {
            Log.warn("CodeGuru Reviewer has default settings only for buckets that are prefixed with "
                     + "codeguru-reviewer. If you choose a different name, read the instructions in the README.");
        }
        if (!Paths.get(repoDir).toFile().isDirectory()) {
            throw new GuruCliException(ErrorCodes.DIR_NOT_FOUND,
                                       repoDir + " is not a valid directory.");
        }
        config.setRootDir(Paths.get(repoDir).toRealPath());
        if (this.sourceDirs == null || this.sourceDirs.isEmpty()) {
            this.sourceDirs = Arrays.asList(config.getRootDir().toString());
        }
        sourceDirs.forEach(sourceDir -> {
            val path = Paths.get(sourceDir);
            if (!path.toFile().isDirectory()) {
                throw new GuruCliException(ErrorCodes.DIR_NOT_FOUND,
                                           sourceDir + " is not a valid directory.");
            }
            if (!path.toAbsolutePath().normalize().startsWith(config.getRootDir())) {
                throw new GuruCliException(ErrorCodes.DIR_NOT_FOUND,
                                           sourceDir + " is not a sub-directory of " + config.getRootDir());
            }
        });
        if (this.buildDirs != null) {
            buildDirs.forEach(buildDir -> {
                if (!Paths.get(buildDir).toFile().isDirectory()) {
                    throw new GuruCliException(ErrorCodes.DIR_NOT_FOUND,
                                               buildDir + " is not a valid directory.");
                }
            });
        }
        config.setKeyId(this.kmsKeyId);
    }

    private void tryDeleteS3Object(final S3Client s3Client, final String s3Bucket, final String s3Key) {
        try {
            if (s3Key != null) {
                s3Client.deleteObject(DeleteObjectRequest.builder().bucket(s3Bucket).key(s3Key).build());
            }
        } catch (Exception e) {
            Log.warn("Failed to delete %s from %s. Please delete the object by hand.", s3Key, s3Bucket);
        }
    }

    protected void createAWSClients(final Configuration config) {
        val credentials = getCredentials();
        try {
            config.setRegion(regionName);
            val callerIdentity =
                StsClient.builder()
                         .credentialsProvider(credentials)
                         .region(Region.of(regionName))
                         .build().getCallerIdentity();
            config.setAccountId(callerIdentity.account());
            config.setGuruFrontendService(getNewGuruClient(credentials));
            config.setS3Client(getS3Client(credentials));
        } catch (IllegalArgumentException e) {
            // profile could not be found
            throw new GuruCliException(ErrorCodes.AWS_INIT_ERROR,
                                       "Error accessing the provided profile. " + this.profileName
                                       + "Ensure that the spelling is correct and"
                                       + " that the role has access to CodeGuru and S3.");
        } catch (SdkClientException e) {
            throw new GuruCliException(ErrorCodes.AWS_INIT_ERROR,
                                       "No AWS credentials found. Use 'aws configure' to set them up.");
        }
    }

    private AwsCredentialsProvider getCredentials() {
        if (profileName == null || profileName.replaceAll("\\s+", "").length() == 0) {
            return DefaultCredentialsProvider.create();
        }
        return ProfileCredentialsProvider.create(profileName);
    }

    private CodeGuruReviewerClient getNewGuruClient(AwsCredentialsProvider credentialsProvider) {
        final String endpoint = String.format(REVIEWER_ENDPOINT_PATTERN, regionName);
        return CodeGuruReviewerClient.builder()
                                     .credentialsProvider(credentialsProvider)
                                     .endpointOverride(URI.create(endpoint))
                                     .region(Region.of(regionName))
                                     .build();
    }

    private S3Client getS3Client(AwsCredentialsProvider credentialsProvider) {
        return S3Client.builder()
                       .credentialsProvider(credentialsProvider)
                       .region(Region.of(regionName))
                       .build();
    }
}
