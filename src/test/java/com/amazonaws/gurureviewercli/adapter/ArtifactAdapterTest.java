package com.amazonaws.gurureviewercli.adapter;

import com.amazonaws.gurureviewercli.model.Configuration;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

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
                Assertions.assertEquals(2, count, "Unexpected number of files in zip: " + count);
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

    @Test
    public void test_zipAndUpload_regression01() throws Exception {
        val repoDir = Paths.get("./test-data/source-and-class");
        // If we analyze the entire repo without setting build artifacts, we should get 1 archive with 3 files.
        val archivedFiles = getArchivedFileNames(repoDir, Arrays.asList(repoDir), Collections.emptyList());
        Assertions.assertEquals(1, archivedFiles.keySet().size());
        val firstKey = archivedFiles.keySet().iterator().next();
        Assertions.assertEquals(3, archivedFiles.get(firstKey).size());
    }

    @Test
    public void test_zipAndUpload_regression02() throws Exception {
        val repoDir = Paths.get("./test-data/source-and-class");
        val srcDir = repoDir.resolve("src");
        // if we analyze only the src dir without setting build artifacts, we should get 1 archive with 1 file.
        val archivedFiles = getArchivedFileNames(repoDir, Arrays.asList(srcDir), Collections.emptyList());
        Assertions.assertEquals(1, archivedFiles.keySet().size());
        val firstKey = archivedFiles.keySet().iterator().next();
        Assertions.assertEquals(1, archivedFiles.get(firstKey).size());
    }

    @Test
    public void test_zipAndUpload_regression03() throws Exception {
        val repoDir = Paths.get("./test-data/source-and-class");
        val srcDir = repoDir.resolve("src");
        val buildDir = repoDir.resolve("target");
        // If we analyze the src and build dir, we should get 2 archives with 1 file each.
        val archivedFiles = getArchivedFileNames(repoDir, Arrays.asList(srcDir), Arrays.asList(buildDir));
        Assertions.assertEquals(2, archivedFiles.keySet().size());
        val keyIterator = archivedFiles.keySet().iterator();
        val firstKey = keyIterator.next();
        Assertions.assertEquals(1, archivedFiles.get(firstKey).size());
        val secondKey = keyIterator.next();
        Assertions.assertEquals(1, archivedFiles.get(secondKey).size());
    }

    @Test
    public void test_zipAndUpload_regression04() throws Exception {
        val repoDir = Paths.get("./test-data/source-and-class");
        val buildDir = repoDir.resolve("target");
        // If we analyze the root and build dir, we should get 2 archives. The source archive should contain 2,
        // and the build archive should contain 1.
        // Note that the source artifact would actually contain 3 files, but we remove the build artifact from it.
        val archivedFiles = getArchivedFileNames(repoDir, Arrays.asList(repoDir), Arrays.asList(buildDir));
        Assertions.assertEquals(2, archivedFiles.keySet().size());
        val keyIterator = archivedFiles.keySet().iterator();
        val firstKey = keyIterator.next();
        val secondKey = keyIterator.next();
        if (firstKey.contains("analysis-src-")) {
            Assertions.assertEquals(2, archivedFiles.get(firstKey).size());
            Assertions.assertEquals(1, archivedFiles.get(secondKey).size());
        } else if (firstKey.contains("analysis-bin-")) {
            Assertions.assertEquals(1, archivedFiles.get(firstKey).size());
            Assertions.assertEquals(2, archivedFiles.get(secondKey).size());
        } else {
            // unexpected case
            Assertions.assertTrue(false);
        }
    }

    private Map<String, List<String>> getArchivedFileNames(final Path repoDir,
                                                           final List<Path> relativeSrcDirs,
                                                           final List<Path> relativeBuildDirs) throws Exception {
        val tempDir = Files.createTempDirectory("test_setupRegressionScan");
        val bucketName = "some-bucket";

        val config = Configuration.builder()
                .s3Client(s3client)
                .interactiveMode(false)
                .build();

        // maps archive name to the list of its files.
        val archiveFileMap = new HashMap<String, List<String>>();
        Answer<Object> answer = invocationOnMock -> {
            Path filePath = invocationOnMock.getArgument(1);
            Assertions.assertTrue(filePath.toFile().isFile());
            try (val zipFile = new ZipFile(filePath.toFile())) {
                archiveFileMap.putIfAbsent(filePath.toString(), new ArrayList<>());
                val entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    archiveFileMap.get(filePath.toString()).add(entries.nextElement().getName());
                }
            }
            return null;
        };
        doAnswer(answer).when(s3client).putObject(any(PutObjectRequest.class), any(Path.class));
        ArtifactAdapter.zipAndUpload(config, tempDir, repoDir, relativeSrcDirs, relativeBuildDirs, bucketName);

        return archiveFileMap;
    }
}