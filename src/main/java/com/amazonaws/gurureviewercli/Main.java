package com.amazonaws.gurureviewercli;


import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import lombok.val;
import org.beryx.textio.TextIO;
import org.beryx.textio.system.SystemTextTerminal;

import com.amazonaws.SdkClientException;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.gurureviewercli.adapter.GitAdapter;
import com.amazonaws.gurureviewercli.adapter.ResultsAdapter;
import com.amazonaws.gurureviewercli.adapter.ScanAdapter;
import com.amazonaws.gurureviewercli.exceptions.GuruCliException;
import com.amazonaws.gurureviewercli.model.Configuration;
import com.amazonaws.gurureviewercli.model.ErrorCodes;
import com.amazonaws.gurureviewercli.model.GitMetaData;
import com.amazonaws.gurureviewercli.util.Log;
import com.amazonaws.services.codegurureviewer.AmazonCodeGuruReviewer;
import com.amazonaws.services.codegurureviewer.AmazonCodeGuruReviewerClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.securitytoken.model.GetCallerIdentityRequest;

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

    @Parameter(names = {"--repository", "-r"},
               description = "The directory of the git repo that should be analyzed.",
               required = true)
    private String repoDir;

    @Parameter(names = {"--src", "-s"},
               description = "Source directories to be analyzed. Can be used multiple times.")
    private List<String> sourceDirs = Arrays.asList("./");

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
            // sanity check if repo is valid git.
            val gitMetaData = main.readGitMetaData(config, Paths.get(main.repoDir).normalize());

            String repoName = gitMetaData.getRepoRoot().toFile().getName();
            config.setRepoName(repoName);

            val scanMetaData = ScanAdapter.startScan(config, gitMetaData, main.sourceDirs, main.buildDirs);
            val results = ScanAdapter.fetchResults(config, scanMetaData);

            val outputPath = Paths.get(main.outputDir);
            if (!outputPath.toFile().exists()) {
                if (outputPath.toFile().mkdirs()) {
                    Log.println("Directory %s already exists; previous results may be overwritten.", outputPath);
                }
            }
            ResultsAdapter.saveResults(outputPath, results, scanMetaData);

            Log.info("Analysis finished.");
        } catch (GuruCliException e) {
            Log.error("%s: %s", e.getErrorCode(), e.getMessage());
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

    protected GitMetaData readGitMetaData(final Configuration config, final Path repoRoot) {
        if (commitRange != null) {
            val commits = commitRange.split(":");
            if (commits.length != 2) {
                throw new GuruCliException(ErrorCodes.GIT_INVALID_COMMITS,
                                           "Invalid value for --commit-range. Use '[before commit]:[after commit]'.");
            }
            config.setBeforeCommit(commits[0]);
            config.setAfterCommit(commits[1]);
        }

        return GitAdapter.getGitMetatData(config, repoRoot);
    }

    private void validateInitialConfig(final Configuration config) {
        if (config.getBucketName() != null && !config.getBucketName().startsWith("codeguru-reviewer-")) {
            throw new GuruCliException(ErrorCodes.BAD_BUCKET_NAME,
                                       config.getBucketName() + " is not a valid bucket name for CodeGuru.");
        }
        if (Paths.get(repoDir).toFile().isDirectory()) {
            throw new GuruCliException(ErrorCodes.DIR_NOT_FOUND,
                                       repoDir + " is not a valid directory.");
        }
        if (this.sourceDirs != null) {
            sourceDirs.forEach(sourceDir -> {
                if (Paths.get(sourceDir).toFile().isDirectory()) {
                    throw new GuruCliException(ErrorCodes.DIR_NOT_FOUND,
                                               repoDir + " is not a valid directory.");
                }
            });
        }
        if (this.buildDirs != null) {
            buildDirs.forEach(buildDir -> {
                if (Paths.get(buildDir).toFile().isDirectory()) {
                    throw new GuruCliException(ErrorCodes.DIR_NOT_FOUND,
                                               repoDir + " is not a valid directory.");
                }
            });
        }
    }

    protected void createAWSClients(final Configuration config) {
        val credentials = getCredentials();
        try {
            config.setRegion(regionName);
            val callerIdentity =
                AWSSecurityTokenServiceClientBuilder.standard()
                                                    .withRegion(this.regionName)
                                                    .withCredentials(credentials).build()
                                                    .getCallerIdentity(new GetCallerIdentityRequest());
            config.setAccountId(callerIdentity.getAccount());
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

    private AWSCredentialsProvider getCredentials() {
        if (profileName == null || profileName.replaceAll("\\s+", "").length() == 0) {
            return new DefaultAWSCredentialsProviderChain();
        }
        return new ProfileCredentialsProvider(profileName);
    }

    private AmazonCodeGuruReviewer getNewGuruClient(AWSCredentialsProvider credentialsProvider) {
        String endpoint = String.format(REVIEWER_ENDPOINT_PATTERN, regionName);
        val endpointConfig = new AwsClientBuilder.EndpointConfiguration(endpoint, regionName);
        return AmazonCodeGuruReviewerClientBuilder.standard()
                                                  .withCredentials(credentialsProvider)
                                                  .withEndpointConfiguration(endpointConfig)
                                                  .build();
    }

    private AmazonS3 getS3Client(AWSCredentialsProvider credentialsProvider) {
        return AmazonS3ClientBuilder.standard()
                                    .withCredentials(credentialsProvider)
                                    .withRegion(regionName)
                                    .build();
    }
}
