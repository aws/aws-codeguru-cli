package com.amazonaws.gurureviewercli.adapter;

import lombok.val;
import org.beryx.textio.TextIO;
import org.beryx.textio.mock.MockTextTerminal;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.amazonaws.gurureviewercli.exceptions.GuruCliException;
import com.amazonaws.gurureviewercli.model.Configuration;
import com.amazonaws.gurureviewercli.model.ErrorCodes;
import com.amazonaws.services.codegurureviewer.AmazonCodeGuruReviewer;
import com.amazonaws.services.codegurureviewer.model.AssociateRepositoryResult;
import com.amazonaws.services.codegurureviewer.model.DescribeRepositoryAssociationResult;
import com.amazonaws.services.codegurureviewer.model.ListRepositoryAssociationsResult;
import com.amazonaws.services.codegurureviewer.model.RepositoryAssociation;
import com.amazonaws.services.codegurureviewer.model.RepositoryAssociationSummary;
import com.amazonaws.services.s3.AmazonS3;

@ExtendWith(MockitoExtension.class)
class AssociationAdapterTest {

    @Mock
    private AmazonCodeGuruReviewer guruFrontendService;

    @Mock
    private AmazonS3 s3client;

    @Test
    public void test_getAssociatedGuruRepo_associationExists() {
        val fakeArn = "123";
        val expected = new RepositoryAssociation().withAssociationArn(fakeArn);
        val summary = new RepositoryAssociationSummary().withAssociationArn(fakeArn);
        val response = new ListRepositoryAssociationsResult().withRepositoryAssociationSummaries(summary);
        when(guruFrontendService.listRepositoryAssociations(any()))
            .thenReturn(response);
        val describeResponse = new DescribeRepositoryAssociationResult().withRepositoryAssociation(expected);
        when(guruFrontendService.describeRepositoryAssociation(any())).thenReturn(describeResponse);
        val config = Configuration.builder()
                                  .guruFrontendService(guruFrontendService)
                                  .repoName("some-repo-name")
                                  .build();
        val association = AssociationAdapter.getAssociatedGuruRepo(config);
        Assertions.assertEquals(expected.getAssociationArn(), association.getAssociationArn());
    }

    @Test
    public void test_getAssociatedGuruRepo_createNewWithExistingBucket() {
        val bucketName = "some-bucket";
        val fakeArn = "123";
        val expected = new RepositoryAssociation().withAssociationArn(fakeArn);
        val emptyListResponse = new ListRepositoryAssociationsResult().withRepositoryAssociationSummaries();
        when(guruFrontendService.listRepositoryAssociations(any()))
            .thenReturn(emptyListResponse);
        when(s3client.doesBucketExistV2(any())).thenReturn(true);
        when(guruFrontendService.associateRepository(any()))
            .thenReturn(new AssociateRepositoryResult().withRepositoryAssociation(expected));
        val config = Configuration.builder()
                                  .guruFrontendService(guruFrontendService)
                                  .s3Client(s3client)
                                  .repoName("some-repo-name")
                                  .build();
        val association = AssociationAdapter.getAssociatedGuruRepo(config);
        Assertions.assertEquals(expected.getAssociationArn(), association.getAssociationArn());
    }

    @Test
    public void test_getAssociatedGuruRepo_createNewWithCreateBucket() {
        // Same test as test_getAssociatedGuruRepo_createNewWithExistingBucket since creating the bucket does not
        // return anything
        val bucketName = "some-bucket";
        val fakeArn = "123";
        val expected = new RepositoryAssociation().withAssociationArn(fakeArn);
        val emptyListResponse = new ListRepositoryAssociationsResult().withRepositoryAssociationSummaries();
        when(guruFrontendService.listRepositoryAssociations(any()))
            .thenReturn(emptyListResponse);
        when(s3client.doesBucketExistV2(any())).thenReturn(false);
        when(guruFrontendService.associateRepository(any()))
            .thenReturn(new AssociateRepositoryResult().withRepositoryAssociation(expected));
        val config = Configuration.builder()
                                  .guruFrontendService(guruFrontendService)
                                  .interactiveMode(false)
                                  .s3Client(s3client)
                                  .repoName("some-repo-name")
                                  .build();
        val association = AssociationAdapter.getAssociatedGuruRepo(config);
        Assertions.assertEquals(expected.getAssociationArn(), association.getAssociationArn());
    }

    @Test
    public void test_getAssociatedGuruRepo_createNewWithCreateBucketInteractive() {
        val bucketName = "some-bucket";
        val fakeArn = "123";
        val expected = new RepositoryAssociation().withAssociationArn(fakeArn);
        val emptyListResponse = new ListRepositoryAssociationsResult().withRepositoryAssociationSummaries();
        when(guruFrontendService.listRepositoryAssociations(any()))
            .thenReturn(emptyListResponse);
        when(s3client.doesBucketExistV2(any())).thenReturn(false);
        when(guruFrontendService.associateRepository(any()))
            .thenReturn(new AssociateRepositoryResult().withRepositoryAssociation(expected));

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
        Assertions.assertEquals(expected.getAssociationArn(), association.getAssociationArn());
    }

    @Test
    public void test_getAssociatedGuruRepo_createNewWithCreateBucketInteractiveAbort() {
        val bucketName = "some-bucket";
        val emptyListResponse = new ListRepositoryAssociationsResult().withRepositoryAssociationSummaries();
        when(guruFrontendService.listRepositoryAssociations(any()))
            .thenReturn(emptyListResponse);
        when(s3client.doesBucketExistV2(any())).thenReturn(false);

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