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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.amazonaws.gurureviewercli.model.Configuration;
import com.amazonaws.gurureviewercli.model.GitMetaData;
import com.amazonaws.services.codegurureviewer.AmazonCodeGuruReviewer;
import com.amazonaws.services.codegurureviewer.model.CodeReview;
import com.amazonaws.services.codegurureviewer.model.CreateCodeReviewResult;
import com.amazonaws.services.codegurureviewer.model.DescribeRepositoryAssociationResult;
import com.amazonaws.services.codegurureviewer.model.ListRepositoryAssociationsResult;
import com.amazonaws.services.codegurureviewer.model.RepositoryAssociation;
import com.amazonaws.services.codegurureviewer.model.RepositoryAssociationSummary;
import com.amazonaws.services.codegurureviewer.model.S3RepositoryDetails;
import com.amazonaws.services.s3.AmazonS3;

@ExtendWith(MockitoExtension.class)
class ScanAdapterTest {

    @Mock
    private AmazonCodeGuruReviewer guruFrontendService;

    @Mock
    private AmazonS3 s3client;

    @Test
    public void test_startScan_HappyCase() throws Exception {
        // skip the test if the test container stripped to the top level .git folder
        Assumptions.assumeTrue(Paths.get("./.git").toFile().isDirectory());
        val fakeArn = "123";
        val bucketName = "some-bucket";
        val repoDetails = new S3RepositoryDetails().withBucketName(bucketName);
        val expected = new RepositoryAssociation().withAssociationArn(fakeArn)
                                                  .withS3RepositoryDetails(repoDetails);
        val summary = new RepositoryAssociationSummary().withAssociationArn(fakeArn);
        val response = new ListRepositoryAssociationsResult().withRepositoryAssociationSummaries(summary);
        when(guruFrontendService.listRepositoryAssociations(any()))
            .thenReturn(response);
        val describeResponse = new DescribeRepositoryAssociationResult().withRepositoryAssociation(expected);
        when(guruFrontendService.describeRepositoryAssociation(any())).thenReturn(describeResponse);

        val review = new CodeReview().withCodeReviewArn(fakeArn);
        val crResponse = new CreateCodeReviewResult().withCodeReview(review);
        when(guruFrontendService.createCodeReview(any())).thenReturn(crResponse);

        val config = Configuration.builder()
                                  .guruFrontendService(guruFrontendService)
                                  .s3Client(s3client)
                                  .build();
        val gitMetaData = GitMetaData.builder()
                                     .repoRoot(Paths.get("./"))
                                     .build();
        List<String> sourceDirs = Arrays.asList("src");
        List<String>  buildDirs = Arrays.asList();
        ScanAdapter.startScan(config, gitMetaData, sourceDirs, buildDirs);
    }
}