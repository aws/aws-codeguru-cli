package com.amazonaws.gurureviewercli.adapter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipFile;

import lombok.val;
import org.beryx.textio.TextIO;
import org.beryx.textio.mock.MockTextTerminal;
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
    public void test_zipAndUpload_happyCaseSourceOnly() throws Exception {
        val repoDir = Paths.get("./");
        // skip the test if the test container stripped to the top level .git folder
        Assumptions.assumeTrue(repoDir.resolve(".git").toFile().isDirectory());
        val tempDir = Files.createTempDirectory("test_zipAndUpload_happyCase");
        val bucketName = "some-bucket";

        val sourceDirs = Arrays.asList(Paths.get("src"));
        final List<Path> buildDirs = Collections.emptyList();
        val config = Configuration.builder()
                                  .s3Client(s3client)
                                  .build();
        Answer<Object> answer = invocationOnMock -> {
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


    @Test
    public void test_zipAndUpload_happyCaseGitFilesOnly() throws Exception {
        val repoDir = Paths.get("./test-data/two-commits").toRealPath();
        val tempDir = Files.createTempDirectory("test_zipAndUpload_happyCaseGitFilesOnly");
        val bucketName = "some-bucket";

        // only include files from the util dir.
        final List<Path> buildDirs = Collections.emptyList();
        val mockTerminal = new MockTextTerminal();
        // answer No to the question if only files under version control should be scanned.
        mockTerminal.getInputs().add("y");
        val config = Configuration.builder()
                                  .s3Client(s3client)
                                  .interactiveMode(true)
                                  .textIO(new TextIO(mockTerminal))
                                  .versionedFiles(Arrays.asList(repoDir.resolve("test.txt")))
                                  .build();
        Answer<Object> answer = invocationOnMock -> {
            Path filePath = invocationOnMock.getArgument(1);
            Assertions.assertTrue(filePath.toFile().isFile());
            try (val zipFile = new ZipFile(filePath.toFile())) {
                val entries = zipFile.entries();
                int count = 0;
                while (entries.hasMoreElements()) {
                    val s = entries.nextElement().getName();
                    val original = repoDir.resolve(s).toFile();
                    Assertions.assertTrue(original.isFile(), "Not a valid file: " + original);
                    Assertions.assertFalse(s.startsWith(".."));
                    if (!s.startsWith("git/")) {
                        count++; // count the files that are not in the git folder.
                    }
                }
                Assertions.assertEquals(1, count, "Unexpected number of files in zip.");
            }
            return null;
        };
        doAnswer(answer).when(s3client).putObject(any(PutObjectRequest.class), any(Path.class));

        val metaData =
            ArtifactAdapter.zipAndUpload(config, tempDir, repoDir, Arrays.asList(repoDir), buildDirs, bucketName);
        Assertions.assertNull(metaData.getBuildKey());
        Assertions.assertNotNull(metaData.getSourceKey());
    }

    @Test
    public void test_zipAndUpload_happyCaseAllFiles() throws Exception {
        val repoDir = Paths.get("./test-data/two-commits").toRealPath();
        val tempDir = Files.createTempDirectory("test_zipAndUpload_happyCaseGitFilesOnly");
        val bucketName = "some-bucket";

        // only include files from the util dir.
        final List<Path> buildDirs = Collections.emptyList();
        val mockTerminal = new MockTextTerminal();
        // answer No to the question if only files under version control should be scanned.
        mockTerminal.getInputs().add("n");
        val config = Configuration.builder()
                                  .s3Client(s3client)
                                  .interactiveMode(true)
                                  .textIO(new TextIO(mockTerminal))
                                  .versionedFiles(Arrays.asList(repoDir.resolve("test.txt")))
                                  .build();
        Answer<Object> answer = invocationOnMock -> {
            Path filePath = invocationOnMock.getArgument(1);
            Assertions.assertTrue(filePath.toFile().isFile());
            try (val zipFile = new ZipFile(filePath.toFile())) {
                val entries = zipFile.entries();
                int count = 0;
                while (entries.hasMoreElements()) {
                    val s = entries.nextElement().getName();
                    val original = repoDir.resolve(s).toFile();
                    Assertions.assertTrue(original.isFile(), "Not a valid file: " + original);
                    if (!s.startsWith("git/")) {
                        count++; // count the files that are not in the git folder.
                    }
                }
                Assertions.assertEquals(2, count, "Unexpected number of files in zip.");
            }
            return null;
        };
        doAnswer(answer).when(s3client).putObject(any(PutObjectRequest.class), any(Path.class));

        val metaData =
            ArtifactAdapter.zipAndUpload(config, tempDir, repoDir, Arrays.asList(repoDir), buildDirs, bucketName);
        Assertions.assertNull(metaData.getBuildKey());
        Assertions.assertNotNull(metaData.getSourceKey());
    }

    @Test
    public void test_zipAndUpload_happyCaseBuildDir() throws Exception {

        val tempDir = Files.createTempDirectory("test_zipAndUpload_happyCaseGitFilesOnly");
        val bucketName = "some-bucket";

        // only include files from the util dir.
        val repoDir = Paths.get("./test-data/fake-repo");
        val buildArtifacts = repoDir.resolve("build-dir/lib");
        final List<Path> buildDirs = Arrays.asList(buildArtifacts);

        val config = Configuration.builder()
                                  .s3Client(s3client)
                                  .interactiveMode(false)
                                  .build();

        Answer<Object> answer = invocationOnMock -> {
            Path filePath = invocationOnMock.getArgument(1);
            if (!filePath.toString().contains("analysis-bin")) {
                return null; // only look at the artifacts.
            }
            Assertions.assertTrue(filePath.toFile().isFile());
            try (val zipFile = new ZipFile(filePath.toFile())) {
                val entries = zipFile.entries();
                int count = 0;
                while (entries.hasMoreElements()) {
                    val s = entries.nextElement().getName();
                    Assertions.assertTrue(s.endsWith("included.txt"));
                    count++; // count the files that are not in the git folder.
                }
                Assertions.assertEquals(1, count);
            }
            return null;
        };
        doAnswer(answer).when(s3client).putObject(any(PutObjectRequest.class), any(Path.class));

        val metaData =
            ArtifactAdapter.zipAndUpload(config, tempDir,
                                         repoDir,
                                         Arrays.asList(repoDir),
                                         Arrays.asList(buildArtifacts),
                                         bucketName);
        Assertions.assertNotNull(metaData.getBuildKey());
        Assertions.assertNotNull(metaData.getSourceKey());
    }
}
