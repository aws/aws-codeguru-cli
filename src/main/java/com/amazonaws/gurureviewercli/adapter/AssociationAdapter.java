package com.amazonaws.gurureviewercli.adapter;

import java.util.concurrent.TimeUnit;

import lombok.val;
import software.amazon.awssdk.services.codegurureviewer.model.AssociateRepositoryRequest;
import software.amazon.awssdk.services.codegurureviewer.model.DescribeRepositoryAssociationRequest;
import software.amazon.awssdk.services.codegurureviewer.model.DescribeRepositoryAssociationResponse;
import software.amazon.awssdk.services.codegurureviewer.model.EncryptionOption;
import software.amazon.awssdk.services.codegurureviewer.model.KMSKeyDetails;
import software.amazon.awssdk.services.codegurureviewer.model.ListRepositoryAssociationsRequest;
import software.amazon.awssdk.services.codegurureviewer.model.ProviderType;
import software.amazon.awssdk.services.codegurureviewer.model.Repository;
import software.amazon.awssdk.services.codegurureviewer.model.RepositoryAssociation;
import software.amazon.awssdk.services.codegurureviewer.model.RepositoryAssociationState;
import software.amazon.awssdk.services.codegurureviewer.model.S3Repository;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

import com.amazonaws.gurureviewercli.exceptions.GuruCliException;
import com.amazonaws.gurureviewercli.model.Configuration;
import com.amazonaws.gurureviewercli.model.ErrorCodes;
import com.amazonaws.gurureviewercli.util.Log;

/**
 * Utility class to get or create a CodeGuru Reviewer Repository association.
 */
public final class AssociationAdapter {

    private static final String BUCKET_NAME_PATTERN = "codeguru-reviewer-cli-%s-%s";
    private static final long WAIT_TIME_IN_SECONDS = 1L;

    /**
     * Get or create a CodeGuru Repository Association (and, if necessary an S3 bucket).
     *
     * @param config The {@link Configuration} with name of repo, account, and region.
     * @return A CodeGuru Repository association.
     */
    public static RepositoryAssociation getAssociatedGuruRepo(final Configuration config) {
        val guruFrontendService = config.getGuruFrontendService();
        val repositoryAssociationsRequest =
            ListRepositoryAssociationsRequest.builder()
                                             .providerTypes(ProviderType.S3_BUCKET)
                                             .names(config.getRepoName())
                                             .build();
        val associationResults = guruFrontendService.listRepositoryAssociations(repositoryAssociationsRequest);
        if (associationResults.repositoryAssociationSummaries().size() == 1) {
            val summary = associationResults.repositoryAssociationSummaries().get(0);
            val describeAssociationRequest =
                DescribeRepositoryAssociationRequest.builder().associationArn(summary.associationArn()).build();
            val association =
                guruFrontendService.describeRepositoryAssociation(describeAssociationRequest)
                                   .repositoryAssociation();
            if (!RepositoryAssociationState.ASSOCIATED.equals(association.state())) {
                val msg = String.format("Repository association in unexpected state %s: %s",
                                        association.state(),
                                        association.stateReason());
                throw new GuruCliException(ErrorCodes.ASSOCIATION_FAILED, msg);
            }
            if (config.getKeyId() != null &&
                !config.getKeyId().equals(association.kmsKeyDetails().kmsKeyId())) {
                val msg = String.format("Provided KMS Key alias %s for repository %s does "
                                        + "not match existing key: %s",
                                        config.getKeyId(),
                                        association.name(),
                                        association.kmsKeyDetails().kmsKeyId());
                throw new GuruCliException(ErrorCodes.ASSOCIATION_FAILED, msg);
            }
            return association;
        } else if (associationResults.repositoryAssociationSummaries().isEmpty()) {
            return createBucketAndAssociation(config);
        } else {
            throw new RuntimeException("Found more than one matching association: " + associationResults);
        }
    }

    private static RepositoryAssociation createBucketAndAssociation(final Configuration config) {
        final String bucketName;
        if (config.getBucketName() != null) {
            if (!config.getBucketName().startsWith("codeguru-reviewer-")) {
                throw new GuruCliException(ErrorCodes.BAD_BUCKET_NAME,
                                           config.getBucketName() + " is not a valid bucket name for CodeGuru.");
            }
            bucketName = config.getBucketName();
        } else {
            bucketName = String.format(BUCKET_NAME_PATTERN, config.getAccountId(), config.getRegion());
        }
        try {
            config.getS3Client().headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
        } catch (NoSuchBucketException e) {
            Log.info("CodeGuru Reviewer requires an S3 bucket to upload the analysis artifacts to.");
            val createBucket =
                !config.isInteractiveMode() ||
                config.getTextIO()
                      .newBooleanInputReader()
                      .withTrueInput("y")
                      .withFalseInput("n")
                      .read("Do you want to create a new S3 bucket: " + bucketName, bucketName);
            if (createBucket) {
                Log.info("Creating new bucket: %s", bucketName);
                config.getS3Client().createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
            } else {
                throw new GuruCliException(ErrorCodes.USER_ABORT, "CodeGuru needs an S3 bucket to continue.");
            }

        }

        val repository = Repository.builder()
                                   .s3Bucket(S3Repository.builder()
                                                         .bucketName(bucketName)
                                                         .name(config.getRepoName())
                                                         .build())
                                   .build();

        AssociateRepositoryRequest associateRequest;
        if (config.getKeyId() != null) {
            val keyDetails = KMSKeyDetails.builder()
                                          .encryptionOption(EncryptionOption.CUSTOMER_MANAGED_CMK)
                                          .kmsKeyId(config.getKeyId())
                                          .build();
            associateRequest = AssociateRepositoryRequest.builder()
                                                         .repository(repository)
                                                         .kmsKeyDetails(keyDetails)
                                                         .build();
        } else {
            associateRequest = AssociateRepositoryRequest.builder().repository(repository).build();
        }
        val associateResponse = config.getGuruFrontendService().associateRepository(associateRequest);
        val associationArn = associateResponse.repositoryAssociation().associationArn();
        Log.print("Creating association ");
        DescribeRepositoryAssociationRequest associationRequest =
            DescribeRepositoryAssociationRequest.builder().associationArn(associationArn).build();

        DescribeRepositoryAssociationResponse associationResponse =
            config.getGuruFrontendService().describeRepositoryAssociation(associationRequest);
        while (associationResponse != null) {
            val association = associationResponse.repositoryAssociation();
            if (RepositoryAssociationState.ASSOCIATED.equals(association.state())) {
                Log.println(" done");
                Log.print("Created new repository association: ");
                Log.awsUrl("?region=%s#/ciworkflows/associationdetails/%s", config.getRegion(),
                           association.associationArn());
                return association;
            } else if (RepositoryAssociationState.ASSOCIATING.equals(association.state())) {
                Log.print(".");
                try {
                    Thread.sleep(TimeUnit.SECONDS.toMillis(WAIT_TIME_IN_SECONDS));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            } else {
                val msg = String.format("Repository association in unexpected state %s: %s",
                                        association.state(),
                                        association.stateReason());
                throw new GuruCliException(ErrorCodes.ASSOCIATION_FAILED, msg);
            }
            associationResponse = config.getGuruFrontendService().describeRepositoryAssociation(associationRequest);
        }
        throw new GuruCliException(ErrorCodes.ASSOCIATION_FAILED, "Unexpected error during association");
    }

    private AssociationAdapter() {
        // do not instantiate
    }
}
