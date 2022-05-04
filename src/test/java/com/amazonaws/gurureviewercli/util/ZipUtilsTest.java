package com.amazonaws.gurureviewercli.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ZipUtilsTest {

    private Path workDir;

    @BeforeEach
    void beforeEach() throws IOException {
        workDir = Files.createTempDirectory("zip-files");
    }

    @AfterEach
    void afterEach() throws IOException {
        Files.walk(workDir)
             .sorted(Comparator.reverseOrder())
             .map(Path::toFile)
             .forEach(File::delete);
    }

    @Test
    void test_packUnpack() throws IOException {
        val testDir = Paths.get("test-data/fake-repo");
        val zipName = workDir.resolve("test.zip").toString();
        ZipUtils.pack(Arrays.asList(testDir), testDir, zipName);
        try (ZipFile zipFile = new ZipFile(zipName)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            val expectedFileNames =
                new HashSet<String>(Arrays.asList("build-dir/should-not-be-included.txt",
                                                  "build-dir/lib/included.txt",
                                                  "should-not-be-included.txt"));
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                Assertions.assertFalse(entry.toString().contains("\\"), "Unexpected zip entry " + entry);
                Assertions.assertTrue(expectedFileNames.contains(entry.toString()));
                expectedFileNames.remove(entry.toString());
            }
            Assertions.assertTrue(expectedFileNames.isEmpty());
        }
    }

    /*
      If a aws-codeguru-reviewer.yml file is present, it has to be included in the zip file even if the root folder
      is not mentioned in the list of source directories.
     */
    @Test
    void test_packUnpackWithConfig() throws IOException {
        val testDir = Paths.get("test-data/fake-repo-with-config");
        val zipName = workDir.resolve("test.zip").toString();
        ZipUtils.pack(Arrays.asList(testDir.resolve("src-dir")), testDir, zipName);
        try (ZipFile zipFile = new ZipFile(zipName)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            val expectedFileNames =
                new HashSet<String>(Arrays.asList("src-dir/included-src.txt",
                                                  "aws-codeguru-reviewer.yml"));
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                Assertions.assertFalse(entry.toString().contains("\\"), "Unexpected zip entry " + entry);
                Assertions.assertTrue(expectedFileNames.contains(entry.toString()));
                expectedFileNames.remove(entry.toString());
            }
            Assertions.assertTrue(expectedFileNames.isEmpty());
        }
    }
}
