package com.amazonaws.gurureviewercli.adapter;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.beust.jcommander.internal.Nullable;
import lombok.val;

import com.amazonaws.gurureviewercli.model.Configuration;
import com.amazonaws.gurureviewercli.model.GitMetaData;
import com.amazonaws.gurureviewercli.model.ScanMetaData;
import com.amazonaws.gurureviewercli.util.Log;
import com.amazonaws.services.codegurureviewer.AmazonCodeGuruReviewer;
import com.amazonaws.services.codegurureviewer.model.AnalysisType;
import com.amazonaws.services.codegurureviewer.model.CodeArtifacts;
import com.amazonaws.services.codegurureviewer.model.CodeReviewType;
import com.amazonaws.services.codegurureviewer.model.CommitDiffSourceCodeType;
import com.amazonaws.services.codegurureviewer.model.CreateCodeReviewRequest;
import com.amazonaws.services.codegurureviewer.model.DescribeCodeReviewRequest;
import com.amazonaws.services.codegurureviewer.model.DescribeCodeReviewResult;
import com.amazonaws.services.codegurureviewer.model.EventInfo;
import com.amazonaws.services.codegurureviewer.model.ListRecommendationsRequest;
import com.amazonaws.services.codegurureviewer.model.ListRecommendationsResult;
import com.amazonaws.services.codegurureviewer.model.RecommendationSummary;
import com.amazonaws.services.codegurureviewer.model.RepositoryAnalysis;
import com.amazonaws.services.codegurureviewer.model.RepositoryAssociation;
import com.amazonaws.services.codegurureviewer.model.RepositoryHeadSourceCodeType;
import com.amazonaws.services.codegurureviewer.model.RequestMetadata;
import com.amazonaws.services.codegurureviewer.model.S3BucketRepository;
import com.amazonaws.services.codegurureviewer.model.S3RepositoryDetails;
import com.amazonaws.services.codegurureviewer.model.SourceCodeType;
import com.amazonaws.services.codegurureviewer.model.ValidationException;
import com.amazonaws.services.codegurureviewer.model.VendorName;

/**
 * Wraps the commands to start a code-review and to poll and download the results.
 */
public final class ScanAdapter {

    private static final String SCAN_PREFIX_NAME = "codeguru-reviewer-cli-";

    private static final long WAIT_TIME_IN_SECONDS = 2L;

    public static ScanMetaData startScan(final Configuration config,
                                         final GitMetaData gitMetaData,
                                         final List<String> sourceDirs,
                                         final List<String> buildDirs) throws IOException {
        val association = AssociationAdapter.getAssociatedGuruRepo(config);
        val bucketName = association.getS3RepositoryDetails().getBucketName();
        Log.info("Starting analysis of %s with association %s and S3 bucket %s",
                 gitMetaData.getRepoRoot(), association.getAssociationArn(), bucketName);

        try {
            val tempDir = Files.createTempDirectory("artifact-packing-dir");
            val metadata = ArtifactAdapter.zipAndUpload(config, tempDir, gitMetaData.getRepoRoot(),
                                                        sourceDirs, buildDirs, bucketName);

            val request = createRepoAnalysisRequest(gitMetaData, metadata.getSourceKey(),
                                                    metadata.getBuildKey(), association);

            val response = config.getGuruFrontendService().createCodeReview(request);
            if (response == null) {
                throw new RuntimeException("Failed to start scan: " + request);
            }

            Log.print("Started new CodeGuru Reviewer scan: ");
            Log.awsUrl("?region=%s#/codereviews/details/%s", config.getRegion(),
                       response.getCodeReview().getCodeReviewArn());

            metadata.setCodeReviewArn(response.getCodeReview().getCodeReviewArn());
            metadata.setAssociationArn(association.getAssociationArn());
            metadata.setRegion(config.getRegion());
            return metadata;
        } catch (ValidationException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<RecommendationSummary> fetchResults(final Configuration config,
                                                           final ScanMetaData scanMetaData) {
        val reviewARN = scanMetaData.getCodeReviewArn();
        val describeReviewRequest = new DescribeCodeReviewRequest().withCodeReviewArn(reviewARN);
        DescribeCodeReviewResult response = config.getGuruFrontendService().describeCodeReview(describeReviewRequest);
        while (response != null) {
            if ("Completed".equals(response.getCodeReview().getState())) {
                Log.println(":)");
                return downloadResults(config.getGuruFrontendService(), reviewARN);
            } else if ("Pending".equals(response.getCodeReview().getState())) {
                Log.print(".");
                try {
                    Thread.sleep(TimeUnit.SECONDS.toMillis(WAIT_TIME_IN_SECONDS));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            } else if ("Failed".equals(response.getCodeReview().getState())) {
                val msg = String.format("CodeGuru scan failed for ARN %s: %s%nCheck the AWS Console for more detail",
                                        reviewARN, response.getCodeReview().getStateReason());
                throw new RuntimeException(msg);
            } else {
                val msg = String.format("CodeGuru scan is in an unexpected state %s: %s%n"
                                        + "Check the AWS Console for more detail",
                                        response.getCodeReview().getState(), response.getCodeReview().getStateReason());
                throw new RuntimeException(msg);
            }
            response = config.getGuruFrontendService().describeCodeReview(describeReviewRequest);
        }
        throw new RuntimeException("Unable to find information for scan " + reviewARN);
    }

    private static List<RecommendationSummary> downloadResults(final AmazonCodeGuruReviewer guruFrontendService,
                                                               final String reviewARN) {
        val listRequest = new ListRecommendationsRequest().withCodeReviewArn(reviewARN);
        val recommendations = new ArrayList<RecommendationSummary>();
        String nextToken = null;
        do {
            listRequest.setNextToken(nextToken);
            ListRecommendationsResult paginatedResult = guruFrontendService.listRecommendations(listRequest);
            recommendations.addAll(paginatedResult.getRecommendationSummaries());
            nextToken = paginatedResult.getNextToken();
        } while(nextToken != null);
        return recommendations;
    }

    private static CreateCodeReviewRequest createRepoAnalysisRequest(final GitMetaData gitMetaData,
                                                                     final String sourceKey,
                                                                     final @Nullable String buildArtifactKey,
                                                                     final RepositoryAssociation association) {
        final CodeArtifacts codeArtifacts;
        final AnalysisType[] analysisTypes;
        if (buildArtifactKey == null) {
            codeArtifacts = new CodeArtifacts().withSourceCodeArtifactsObjectKey(sourceKey);
            analysisTypes = new AnalysisType[]{AnalysisType.CodeQuality};
        } else {
            codeArtifacts = new CodeArtifacts().withSourceCodeArtifactsObjectKey(sourceKey)
                                               .withBuildArtifactsObjectKey(buildArtifactKey);
            analysisTypes = new AnalysisType[]{AnalysisType.Security, AnalysisType.CodeQuality};
        }

        val s3repoDetails = new S3RepositoryDetails().withBucketName(association.getS3RepositoryDetails()
                                                                                .getBucketName())
                                                     .withCodeArtifacts(codeArtifacts);
        val s3repo = new S3BucketRepository().withName(association.getName())
                                             .withDetails(s3repoDetails);

        val sourceCodeType = getSourceCodeType(s3repo, gitMetaData);

        val repoAnalysis = new RepositoryAnalysis().withSourceCodeType(sourceCodeType);

        val reviewType = new CodeReviewType().withRepositoryAnalysis(repoAnalysis)
                                             .withAnalysisTypes(analysisTypes);

        return new CreateCodeReviewRequest().withType(reviewType)
                                            .withName(SCAN_PREFIX_NAME + UUID.randomUUID().toString())
                                            .withRepositoryAssociationArn(association.getAssociationArn());
    }

    private static SourceCodeType getSourceCodeType(final S3BucketRepository s3BucketRepository,
                                                    final GitMetaData gitMetaData) {

        val hasDiff = gitMetaData.getBeforeCommit() != null && gitMetaData.getAfterCommit() != null;
        val eventInfo = hasDiff ? new EventInfo().withName("push") : new EventInfo().withName("schedule");
        val requestMetaData = new RequestMetadata().withRequestId(gitMetaData.getPullRequestId())
                                                   .withEventInfo(eventInfo)
                                                   .withRequester(gitMetaData.getUserName())
                                                   .withVendorName(VendorName.GitHub);
        if (hasDiff) {
            val commitDiff = new CommitDiffSourceCodeType().withSourceCommit(gitMetaData.getAfterCommit())
                                                           .withDestinationCommit(gitMetaData.getBeforeCommit());
            val repoHead = new RepositoryHeadSourceCodeType().withBranchName(gitMetaData.getCurrentBranch());
            return new SourceCodeType().withS3BucketRepository(s3BucketRepository)
                                       .withCommitDiff(commitDiff)
                                       .withRepositoryHead(repoHead)
                                       .withRequestMetadata(requestMetaData);
        } else {
            val repoHead = new RepositoryHeadSourceCodeType().withBranchName(gitMetaData.getCurrentBranch());
            return new SourceCodeType().withS3BucketRepository(s3BucketRepository)
                                       .withRepositoryHead(repoHead)
                                       .withRequestMetadata(requestMetaData);
        }
    }

    private ScanAdapter() {
        // do not instantiate
    }
}
