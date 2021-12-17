package com.amazonaws.gurureviewercli.adapter;

import lombok.val;

import com.amazonaws.gurureviewercli.exceptions.GuruCliException;
import com.amazonaws.gurureviewercli.model.Configuration;
import com.amazonaws.gurureviewercli.model.ErrorCodes;
import com.amazonaws.gurureviewercli.util.Log;
import com.amazonaws.services.codegurureviewer.model.AssociateRepositoryRequest;
import com.amazonaws.services.codegurureviewer.model.DescribeRepositoryAssociationRequest;
import com.amazonaws.services.codegurureviewer.model.ListRepositoryAssociationsRequest;
import com.amazonaws.services.codegurureviewer.model.ProviderType;
import com.amazonaws.services.codegurureviewer.model.Repository;
import com.amazonaws.services.codegurureviewer.model.RepositoryAssociation;
import com.amazonaws.services.codegurureviewer.model.S3Repository;
import com.amazonaws.services.s3.model.CreateBucketRequest;

/**
 * Utility class to get or create a CodeGuru Reviewer Repository association.
 */
public final class AssociationAdapter {

    private static final String BUCKET_NAME_PATTERN = "codeguru-reviewer-cli-%s-%s";

    /**
     * Get or create a CodeGuru Repository Association (and, if necessary an S3 bucket).
     *
     * @param config The {@link Configuration} with name of repo, account, and region.
     * @return A CodeGuru Repository association.
     */
    public static RepositoryAssociation getAssociatedGuruRepo(final Configuration config) {
        val guruFrontendService = config.getGuruFrontendService();
        val repositoryAssociationsRequest =
            new ListRepositoryAssociationsRequest().withProviderTypes(ProviderType.S3Bucket)
                                                   .withNames(config.getRepoName());
        val associationResults = guruFrontendService.listRepositoryAssociations(repositoryAssociationsRequest);
        if (associationResults.getRepositoryAssociationSummaries().size() == 1) {
            val summary = associationResults.getRepositoryAssociationSummaries().get(0);
            val describeAssociationRequest =
                new DescribeRepositoryAssociationRequest().withAssociationArn(summary.getAssociationArn());
            return guruFrontendService.describeRepositoryAssociation(describeAssociationRequest)
                                      .getRepositoryAssociation();
        } else if (associationResults.getRepositoryAssociationSummaries().isEmpty()) {
            return createBucketAndAssociation(config);
        } else {
            throw new RuntimeException("Found more than one matching association: " + associationResults);
        }
    }

    private static RepositoryAssociation createBucketAndAssociation(final Configuration config) {
        val bucketName = String.format(BUCKET_NAME_PATTERN, config.getAccountId(), config.getRegion());
        if (!config.getS3Client().doesBucketExistV2(bucketName)) {
            Log.info("CodeGuru Reviewer requires an S3 bucket to upload the analysis artifacts to.");
            val doPackageScan =
                !config.isInteractiveMode() ||
                config.getTextIO()
                      .newBooleanInputReader()
                      .withTrueInput("y")
                      .withFalseInput("n")
                      .read("Do you want to create a new S3 bucket: " + bucketName, bucketName);
            if (doPackageScan) {
                config.getS3Client().createBucket(new CreateBucketRequest(bucketName));
            } else {
                throw new GuruCliException(ErrorCodes.USER_ABORT, "CodeGuru needs an S3 bucket to continue.");
            }
        }
        val repository = new Repository().withS3Bucket(new S3Repository().withName(config.getRepoName())
                                                                         .withBucketName(bucketName));
        val associateRequest = new AssociateRepositoryRequest().withRepository(repository);
        val associateResponse = config.getGuruFrontendService().associateRepository(associateRequest);

        Log.print("Created new repository association: ");
        Log.awsUrl("?region=%s#/ciworkflows/associationdetails/%s", config.getRegion(),
                   associateResponse.getRepositoryAssociation().getAssociationArn());
        return associateResponse.getRepositoryAssociation();
    }

    private AssociationAdapter() {
        // do not instantiate
    }
}
