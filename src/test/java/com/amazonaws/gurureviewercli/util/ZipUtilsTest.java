package com.amazonaws.gurureviewercli.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import lombok.val;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ZipUtilsTest {

    @Test
    public void test_packUnpack() throws IOException {
        val testDir = Paths.get("test-data/fake-repo");
        val zipName = Files.createTempDirectory("zip-files").resolve("test.zip").toString();
        ZipUtils.pack(Arrays.asList(testDir), testDir, zipName);
        ZipFile zipFile = new ZipFile(zipName);
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while(entries.hasMoreElements()){
            ZipEntry entry = entries.nextElement();
            Assertions.assertFalse(entry.toString().contains("\\"));
            System.err.println(entry.getName());
        }
    }
}
