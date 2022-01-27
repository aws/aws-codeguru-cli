package com.amazonaws.gurureviewercli.adapter;

import java.util.Collections;

import lombok.val;
import org.beryx.textio.TextIO;
import org.beryx.textio.mock.MockTextTerminal;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.codegurureviewer.CodeGuruReviewerClient;
import software.amazon.awssdk.services.codegurureviewer.model.AssociateRepositoryRequest;
import software.amazon.awssdk.services.codegurureviewer.model.AssociateRepositoryResponse;
import software.amazon.awssdk.services.codegurureviewer.model.DescribeRepositoryAssociationRequest;
import software.amazon.awssdk.services.codegurureviewer.model.DescribeRepositoryAssociationResponse;
import software.amazon.awssdk.services.codegurureviewer.model.ListRepositoryAssociationsRequest;
import software.amazon.awssdk.services.codegurureviewer.model.ListRepositoryAssociationsResponse;
import software.amazon.awssdk.services.codegurureviewer.model.RepositoryAssociation;
import software.amazon.awssdk.services.codegurureviewer.model.RepositoryAssociationState;
import software.amazon.awssdk.services.codegurureviewer.model.RepositoryAssociationSummary;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.amazonaws.gurureviewercli.exceptions.GuruCliException;
import com.amazonaws.gurureviewercli.model.Configuration;
import com.amazonaws.gurureviewercli.model.ErrorCodes;

@ExtendWith(MockitoExtension.class)
class AssociationAdapterTest {

    @Mock
    private CodeGuruReviewerClient guruFrontendService;

    @Mock
    private S3Client s3client;

    @Test
    public void test_getAssociatedGuruRepo_associationExists() {
        val fakeArn = "123";
        val expected = RepositoryAssociation.builder()
                                            .associationArn(fakeArn)
                                            .state(RepositoryAssociationState.ASSOCIATED)
                                            .build();
        val summary = RepositoryAssociationSummary.builder()
                                                  .associationArn(fakeArn)
                                                  .state(RepositoryAssociationState.ASSOCIATED)
                                                  .build();
        val response = ListRepositoryAssociationsResponse.builder().repositoryAssociationSummaries(summary).build();
        when(guruFrontendService.listRepositoryAssociations(any(ListRepositoryAssociationsRequest.class)))
            .thenReturn(response);
        val describeResponse = DescribeRepositoryAssociationResponse.builder().repositoryAssociation(expected).build();
        when(guruFrontendService.describeRepositoryAssociation(any(DescribeRepositoryAssociationRequest.class)))
            .thenReturn(describeResponse);
        val config = Configuration.builder()
                                  .guruFrontendService(guruFrontendService)
                                  .repoName("some-repo-name")
                                  .build();
        val association = AssociationAdapter.getAssociatedGuruRepo(config);
        Assertions.assertEquals(expected.associationArn(), association.associationArn());
    }

    @Test
    public void test_getAssociatedGuruRepo_createNewWithExistingBucket() {
        val bucketName = "some-bucket";
        val fakeArn = "123";
        val expected = RepositoryAssociation.builder()
                                            .associationArn(fakeArn)
                                            .state(RepositoryAssociationState.ASSOCIATED)
                                            .build();
        val emptyListResponse =
            ListRepositoryAssociationsResponse.builder()
                                              .repositoryAssociationSummaries(Collections.emptyList())
                                              .build();
        when(guruFrontendService.listRepositoryAssociations(any(ListRepositoryAssociationsRequest.class)))
            .thenReturn(emptyListResponse);
        when(s3client.headBucket(any(HeadBucketRequest.class))).thenReturn(HeadBucketResponse.builder().build());
        when(guruFrontendService.associateRepository(any(AssociateRepositoryRequest.class)))
            .thenReturn(AssociateRepositoryResponse.builder().repositoryAssociation(expected).build());
        when(guruFrontendService.describeRepositoryAssociation(any(DescribeRepositoryAssociationRequest.class)))
            .thenReturn(DescribeRepositoryAssociationResponse.builder().repositoryAssociation(expected).build());
        val config = Configuration.builder()
                                  .guruFrontendService(guruFrontendService)
                                  .interactiveMode(false)
                                  .s3Client(s3client)
                                  .repoName("some-repo-name")
                                  .build();
        val association = AssociationAdapter.getAssociatedGuruRepo(config);
        Assertions.assertEquals(expected.associationArn(), association.associationArn());
    }

    @Test
    public void test_getAssociatedGuruRepo_createNewWithCreateBucket() {
        // Same test as test_getAssociatedGuruRepo_createNewWithExistingBucket since creating the bucket does not
        // return anything
        val bucketName = "some-bucket";
        val fakeArn = "123";
        val expected = RepositoryAssociation.builder()
                                            .associationArn(fakeArn)
                                            .state(RepositoryAssociationState.ASSOCIATED)
                                            .build();
        val emptyListResponse =
            ListRepositoryAssociationsResponse.builder()
                                              .repositoryAssociationSummaries(Collections.emptyList())
                                              .build();
        when(guruFrontendService.listRepositoryAssociations(any(ListRepositoryAssociationsRequest.class)))
            .thenReturn(emptyListResponse);
        when(s3client.headBucket(any(HeadBucketRequest.class))).thenThrow(NoSuchBucketException.class);
        when(guruFrontendService.associateRepository(any(AssociateRepositoryRequest.class)))
            .thenReturn(AssociateRepositoryResponse.builder().repositoryAssociation(expected).build());
        when(guruFrontendService.describeRepositoryAssociation(any(DescribeRepositoryAssociationRequest.class)))
            .thenReturn(DescribeRepositoryAssociationResponse.builder().repositoryAssociation(expected).build());
        val config = Configuration.builder()
                                  .guruFrontendService(guruFrontendService)
                                  .interactiveMode(false)
                                  .s3Client(s3client)
                                  .repoName("some-repo-name")
                                  .build();
        val association = AssociationAdapter.getAssociatedGuruRepo(config);
        Assertions.assertEquals(expected.associationArn(), association.associationArn());
    }

    @Test
    public void test_getAssociatedGuruRepo_createNewWithCreateBucketInteractive() {
        val bucketName = "some-bucket";
        val fakeArn = "123";
        val expected = RepositoryAssociation.builder()
                                            .associationArn(fakeArn)
                                            .state(RepositoryAssociationState.ASSOCIATED)
                                            .build();

        val emptyListResponse =
            ListRepositoryAssociationsResponse.builder()
                                              .repositoryAssociationSummaries(Collections.emptyList())
                                              .build();
        when(guruFrontendService.listRepositoryAssociations(any(ListRepositoryAssociationsRequest.class)))
            .thenReturn(emptyListResponse);
        when(s3client.headBucket(any(HeadBucketRequest.class))).thenThrow(NoSuchBucketException.class);
        when(guruFrontendService.associateRepository(any(AssociateRepositoryRequest.class)))
            .thenReturn(AssociateRepositoryResponse.builder().repositoryAssociation(expected).build());
        when(guruFrontendService.describeRepositoryAssociation(any(DescribeRepositoryAssociationRequest.class)))
            .thenReturn(DescribeRepositoryAssociationResponse.builder().repositoryAssociation(expected).build());

        val mockTerminal = new MockTextTerminal();
        mockTerminal.getInputs().add("y");

        val config = Configuration.builder()
                                  .guruFrontendService(guruFrontendService)
                                  .interactiveMode(true)
                                  .s3Client(s3client)
                                  .repoName("some-repo-name")
                                  .textIO(new TextIO(mockTerminal))
                                  .build();
        val association = AssociationAdapter.getAssociatedGuruRepo(config);
        Assertions.assertEquals(expected.associationArn(), association.associationArn());
    }

    @Test
    public void test_getAssociatedGuruRepo_createNewWithCreateBucketInteractiveAbort() {
        val bucketName = "some-bucket";
        val emptyListResponse =
            ListRepositoryAssociationsResponse.builder()
                                              .repositoryAssociationSummaries(Collections.emptyList())
                                              .build();
        when(guruFrontendService.listRepositoryAssociations(any(ListRepositoryAssociationsRequest.class)))
            .thenReturn(emptyListResponse);
        when(s3client.headBucket(any(HeadBucketRequest.class))).thenThrow(NoSuchBucketException.class);

        val mockTerminal = new MockTextTerminal();
        mockTerminal.getInputs().add("n");

        val config = Configuration.builder()
                                  .guruFrontendService(guruFrontendService)
                                  .interactiveMode(true)
                                  .s3Client(s3client)
                                  .repoName("some-repo-name")
                                  .textIO(new TextIO(mockTerminal))
                                  .build();
        GuruCliException ret = Assertions.assertThrows(GuruCliException.class, () ->
            AssociationAdapter.getAssociatedGuruRepo(config));
        Assertions.assertEquals(ErrorCodes.USER_ABORT, ret.getErrorCode());
    }
}