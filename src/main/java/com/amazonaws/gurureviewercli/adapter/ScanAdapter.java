package com.amazonaws.gurureviewercli.adapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.beust.jcommander.internal.Nullable;
import lombok.val;
import software.amazon.awssdk.services.codegurureviewer.CodeGuruReviewerClient;
import software.amazon.awssdk.services.codegurureviewer.model.AnalysisType;
import software.amazon.awssdk.services.codegurureviewer.model.CodeArtifacts;
import software.amazon.awssdk.services.codegurureviewer.model.CodeReviewType;
import software.amazon.awssdk.services.codegurureviewer.model.CommitDiffSourceCodeType;
import software.amazon.awssdk.services.codegurureviewer.model.CreateCodeReviewRequest;
import software.amazon.awssdk.services.codegurureviewer.model.DescribeCodeReviewRequest;
import software.amazon.awssdk.services.codegurureviewer.model.DescribeCodeReviewResponse;
import software.amazon.awssdk.services.codegurureviewer.model.EventInfo;
import software.amazon.awssdk.services.codegurureviewer.model.JobState;
import software.amazon.awssdk.services.codegurureviewer.model.ListRecommendationsRequest;
import software.amazon.awssdk.services.codegurureviewer.model.RecommendationSummary;
import software.amazon.awssdk.services.codegurureviewer.model.RepositoryAnalysis;
import software.amazon.awssdk.services.codegurureviewer.model.RepositoryAssociation;
import software.amazon.awssdk.services.codegurureviewer.model.RepositoryHeadSourceCodeType;
import software.amazon.awssdk.services.codegurureviewer.model.RequestMetadata;
import software.amazon.awssdk.services.codegurureviewer.model.S3BucketRepository;
import software.amazon.awssdk.services.codegurureviewer.model.S3RepositoryDetails;
import software.amazon.awssdk.services.codegurureviewer.model.SourceCodeType;
import software.amazon.awssdk.services.codegurureviewer.model.ValidationException;
import software.amazon.awssdk.services.codegurureviewer.model.VendorName;

import com.amazonaws.gurureviewercli.model.Configuration;
import com.amazonaws.gurureviewercli.model.GitMetaData;
import com.amazonaws.gurureviewercli.model.ScanMetaData;
import com.amazonaws.gurureviewercli.util.Log;


/**
 * Wraps the commands to start a code-review and to poll and download the results.
 */
public final class ScanAdapter {

    private static final String SCAN_PREFIX_NAME = "codeguru-reviewer-cli-";

    private static final long WAIT_TIME_IN_SECONDS = 2L;

    public static ScanMetaData startScan(final Configuration config,
                                         final GitMetaData gitMetaData,
                                         final List<Path> sourceDirs,
                                         final List<Path> buildDirs) throws IOException {
        val association = AssociationAdapter.getAssociatedGuruRepo(config);
        val bucketName = association.s3RepositoryDetails().bucketName();
        Log.info("Starting analysis of %s with association %s and S3 bucket %s",
                 config.getRootDir(), association.associationArn(), bucketName);

        try {
            val tempDir = Files.createTempDirectory("artifact-packing-dir");
            val metadata = ArtifactAdapter.zipAndUpload(config, tempDir, config.getRootDir(),
                                                        sourceDirs, buildDirs, bucketName);

            val request = createRepoAnalysisRequest(gitMetaData, metadata.getSourceKey(),
                                                    metadata.getBuildKey(), association);

            val response = config.getGuruFrontendService().createCodeReview(request);
            if (response == null) {
                throw new RuntimeException("Failed to start scan: " + request);
            }

            Log.print("Started new CodeGuru Reviewer scan: ");
            Log.awsUrl("?region=%s#/codereviews/details/%s", config.getRegion(),
                       response.codeReview().codeReviewArn());

            metadata.setCodeReviewArn(response.codeReview().codeReviewArn());
            metadata.setAssociationArn(association.associationArn());
            metadata.setRegion(config.getRegion());
            return metadata;
        } catch (ValidationException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<RecommendationSummary> fetchResults(final Configuration config,
                                                           final ScanMetaData scanMetaData) {
        val reviewARN = scanMetaData.getCodeReviewArn();
        val describeReviewRequest = DescribeCodeReviewRequest.builder().codeReviewArn(reviewARN).build();
        DescribeCodeReviewResponse response = config.getGuruFrontendService().describeCodeReview(describeReviewRequest);
        while (response != null) {
            val state = response.codeReview().state();
            if (JobState.COMPLETED.equals(state)) {
                Log.println(":)");
                return downloadResults(config.getGuruFrontendService(), reviewARN);
            } else if (JobState.PENDING.equals(state)) {
                Log.print(".");
                try {
                    Thread.sleep(TimeUnit.SECONDS.toMillis(WAIT_TIME_IN_SECONDS));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            } else if (JobState.FAILED.equals(state)) {
                val msg = String.format("CodeGuru scan failed for ARN %s: %s%nCheck the AWS Console for more detail",
                                        reviewARN, response.codeReview().stateReason());
                throw new RuntimeException(msg);
            } else {
                val msg = String.format("CodeGuru scan is in an unexpected state %s: %s%n"
                                        + "Check the AWS Console for more detail",
                                        state, response.codeReview().stateReason());
                throw new RuntimeException(msg);
            }
            response = config.getGuruFrontendService().describeCodeReview(describeReviewRequest);
        }
        throw new RuntimeException("Unable to find information for scan " + reviewARN);
    }

    private static List<RecommendationSummary> downloadResults(final CodeGuruReviewerClient guruFrontendService,
                                                               final String reviewARN) {
        val recommendations = new ArrayList<RecommendationSummary>();
        val listRequest = ListRecommendationsRequest.builder().codeReviewArn(reviewARN).build();
        guruFrontendService.listRecommendationsPaginator(listRequest)
                           .forEach(resp -> recommendations.addAll(resp.recommendationSummaries()));
        return recommendations;
    }

    private static CreateCodeReviewRequest createRepoAnalysisRequest(final GitMetaData gitMetaData,
                                                                     final String sourceKey,
                                                                     final @Nullable String buildArtifactKey,
                                                                     final RepositoryAssociation association) {
        final CodeArtifacts codeArtifacts;
        final AnalysisType[] analysisTypes;
        if (buildArtifactKey == null) {
            codeArtifacts = CodeArtifacts.builder().sourceCodeArtifactsObjectKey(sourceKey).build();
            analysisTypes = new AnalysisType[]{AnalysisType.CODE_QUALITY};
        } else {
            codeArtifacts = CodeArtifacts.builder().sourceCodeArtifactsObjectKey(sourceKey)
                                         .buildArtifactsObjectKey(buildArtifactKey)
                                         .build();
            analysisTypes = new AnalysisType[]{AnalysisType.SECURITY, AnalysisType.CODE_QUALITY};
        }

        val s3repoDetails = S3RepositoryDetails.builder().bucketName(association.s3RepositoryDetails()
                                                                                .bucketName())
                                               .codeArtifacts(codeArtifacts).build();
        val s3repo = S3BucketRepository.builder().name(association.name())
                                       .details(s3repoDetails).build();

        val sourceCodeType = getSourceCodeType(s3repo, gitMetaData);

        val repoAnalysis = RepositoryAnalysis.builder().sourceCodeType(sourceCodeType).build();

        val reviewType = CodeReviewType.builder().repositoryAnalysis(repoAnalysis)
                                       .analysisTypes(analysisTypes)
                                       .build();

        return CreateCodeReviewRequest.builder().type(reviewType)
                                      .name(SCAN_PREFIX_NAME + UUID.randomUUID().toString())
                                      .repositoryAssociationArn(association.associationArn())
                                      .build();
    }

    private static SourceCodeType getSourceCodeType(final S3BucketRepository s3BucketRepository,
                                                    final GitMetaData gitMetaData) {

        val hasDiff = gitMetaData.getBeforeCommit() != null && gitMetaData.getAfterCommit() != null;
        val eventInfo = hasDiff ? EventInfo.builder().name("push").build() :
                        EventInfo.builder().name("schedule").build();
        val requestMetaData = RequestMetadata.builder().requestId(gitMetaData.getPullRequestId())
                                             .eventInfo(eventInfo)
                                             .requester(gitMetaData.getUserName())
                                             .vendorName(VendorName.GIT_HUB)
                                             .build();
        if (hasDiff) {
            val commitDiff = CommitDiffSourceCodeType.builder().sourceCommit(gitMetaData.getAfterCommit())
                                                     .destinationCommit(gitMetaData.getBeforeCommit())
                                                     .build();
            val repoHead =
                RepositoryHeadSourceCodeType.builder().branchName(gitMetaData.getCurrentBranch()).build();
            return SourceCodeType.builder().s3BucketRepository(s3BucketRepository)
                                 .commitDiff(commitDiff)
                                 .repositoryHead(repoHead)
                                 .requestMetadata(requestMetaData)
                                 .build();
        } else {
            val repoHead =
                RepositoryHeadSourceCodeType.builder().branchName(gitMetaData.getCurrentBranch()).build();
            return SourceCodeType.builder().s3BucketRepository(s3BucketRepository)
                                 .repositoryHead(repoHead)
                                 .requestMetadata(requestMetaData)
                                 .build();
        }
    }

    private ScanAdapter() {
        // do not instantiate
    }
}
