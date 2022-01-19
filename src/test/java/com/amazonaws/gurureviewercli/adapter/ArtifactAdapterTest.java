package com.amazonaws.gurureviewercli.adapter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipFile;

import lombok.val;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.amazonaws.gurureviewercli.model.Configuration;

@ExtendWith(MockitoExtension.class)
class ArtifactAdapterTest {

    @Mock
    private S3Client s3client;

    @Test
    public void test_zipAndUpload_happyCaseSourceOnly() throws Exception{
        val repoDir = Paths.get("./");
        // skip the test if the test container stripped to the top level .git folder
        Assumptions.assumeTrue(repoDir.resolve(".git").toFile().isDirectory());
        val tempDir = Files.createTempDirectory("test_zipAndUpload_happyCase");
        val bucketName = "some-bucket";

        final List<String> sourceDirs = Arrays.asList("src");
        final List<String> buildDirs = Collections.emptyList();
        val config = Configuration.builder()
                                  .s3Client(s3client)
                                  .build();
        Answer<Object> answer = invocationOnMock -> {
            System.err.println(invocationOnMock);
            Path filePath = invocationOnMock.getArgument(1);
            Assertions.assertTrue(filePath.toFile().isFile());
            try (val zipFile = new ZipFile(filePath.toFile())) {
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
        doAnswer(answer).when(s3client).putObject(any(PutObjectRequest.class), any(Path.class));

        val metaData = ArtifactAdapter.zipAndUpload(config, tempDir, repoDir, sourceDirs, buildDirs, bucketName);
        Assertions.assertNull(metaData.getBuildKey());
        Assertions.assertNotNull(metaData.getSourceKey());
    }

}