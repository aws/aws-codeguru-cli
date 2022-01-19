package com.amazonaws.gurureviewercli.adapter;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import lombok.val;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.codegurureviewer.CodeGuruReviewerClient;
import software.amazon.awssdk.services.codegurureviewer.model.CodeReview;
import software.amazon.awssdk.services.codegurureviewer.model.CreateCodeReviewRequest;
import software.amazon.awssdk.services.codegurureviewer.model.CreateCodeReviewResponse;
import software.amazon.awssdk.services.codegurureviewer.model.DescribeRepositoryAssociationRequest;
import software.amazon.awssdk.services.codegurureviewer.model.DescribeRepositoryAssociationResponse;
import software.amazon.awssdk.services.codegurureviewer.model.ListRepositoryAssociationsRequest;
import software.amazon.awssdk.services.codegurureviewer.model.ListRepositoryAssociationsResponse;
import software.amazon.awssdk.services.codegurureviewer.model.RepositoryAssociation;
import software.amazon.awssdk.services.codegurureviewer.model.RepositoryAssociationSummary;
import software.amazon.awssdk.services.codegurureviewer.model.S3RepositoryDetails;
import software.amazon.awssdk.services.s3.S3Client;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.amazonaws.gurureviewercli.model.Configuration;
import com.amazonaws.gurureviewercli.model.GitMetaData;

@ExtendWith(MockitoExtension.class)
class ScanAdapterTest {

    @Mock
    private CodeGuruReviewerClient guruFrontendService;

    @Mock
    private S3Client s3client;

    @Test
    public void test_startScan_HappyCase() throws Exception {
        // skip the test if the test container stripped to the top level .git folder
        Assumptions.assumeTrue(Paths.get("./.git").toFile().isDirectory());
        val fakeArn = "123";
        val bucketName = "some-bucket";
        val repoDetails = S3RepositoryDetails.builder().bucketName(bucketName).build();
        val expected = RepositoryAssociation.builder().associationArn(fakeArn)
                                            .s3RepositoryDetails(repoDetails)
                                            .build();
        val summary = RepositoryAssociationSummary.builder().associationArn(fakeArn).build();
        val response = ListRepositoryAssociationsResponse.builder().repositoryAssociationSummaries(summary).build();
        when(guruFrontendService.listRepositoryAssociations(any(ListRepositoryAssociationsRequest.class)))
            .thenReturn(response);
        val describeResponse = DescribeRepositoryAssociationResponse.builder().repositoryAssociation(expected).build();
        when(guruFrontendService.describeRepositoryAssociation(any(DescribeRepositoryAssociationRequest.class)))
            .thenReturn(describeResponse);

        val review = CodeReview.builder().codeReviewArn(fakeArn).build();
        val crResponse = CreateCodeReviewResponse.builder().codeReview(review).build();
        when(guruFrontendService.createCodeReview(any(CreateCodeReviewRequest.class))).thenReturn(crResponse);

        val config = Configuration.builder()
                                  .guruFrontendService(guruFrontendService)
                                  .s3Client(s3client)
                                  .build();
        val gitMetaData = GitMetaData.builder()
                                     .repoRoot(Paths.get("./"))
                                     .build();
        List<String> sourceDirs = Arrays.asList("src");
        List<String> buildDirs = Arrays.asList();
        ScanAdapter.startScan(config, gitMetaData, sourceDirs, buildDirs);
    }
}