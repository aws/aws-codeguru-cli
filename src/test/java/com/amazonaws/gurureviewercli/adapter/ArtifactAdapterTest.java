package com.amazonaws.gurureviewercli.adapter;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipFile;

import lombok.val;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.amazonaws.gurureviewercli.model.Configuration;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.PutObjectRequest;

@ExtendWith(MockitoExtension.class)
class ArtifactAdapterTest {

    @Mock
    private AmazonS3 s3client;

    @Test
    public void test_zipAndUpload_happyCaseSourceOnly() throws Exception{
        val repoDir = Paths.get("./");
        val tempDir = Files.createTempDirectory("test_zipAndUpload_happyCase");
        val bucketName = "some-bucket";

        final List<String> sourceDirs = Arrays.asList("src");
        final List<String> buildDirs = Arrays.asList();
        val config = Configuration.builder()
                                  .s3Client(s3client)
                                  .build();
        Answer<Object> answer = invocationOnMock -> {
            System.err.println(invocationOnMock);
            PutObjectRequest request = invocationOnMock.getArgument(0);
            Assertions.assertTrue(request.getFile().isFile());
            try (val zipFile = new ZipFile(request.getFile())) {
                val entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    val s = entries.nextElement().getName();
                    val original = repoDir.resolve(s).toFile();
                    Assertions.assertTrue(original.isFile(), "Not a valid file: " + original);
                    Assertions.assertFalse(s.startsWith(".."));
                }
            }
            return null;
        };
        doAnswer(answer).when(s3client).putObject(any());

        val metaData = ArtifactAdapter.zipAndUpload(config, tempDir, repoDir, sourceDirs, buildDirs, bucketName);
        Assertions.assertNull(metaData.getBuildKey());
        Assertions.assertNotNull(metaData.getSourceKey());
    }

}