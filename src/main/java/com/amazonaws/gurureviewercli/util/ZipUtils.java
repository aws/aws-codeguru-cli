package com.amazonaws.gurureviewercli.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import lombok.extern.log4j.Log4j2;
import lombok.val;

/**
 * Util class for ZipFile.
 */
@Log4j2
public final class ZipUtils {

    /**
     * Zip source directory to destination path.
     *
     * @param sourceDirPaths source dir paths
     * @param zipFilePath    destination zip file
     * @throws IOException io exception
     */
    public static void pack(final List<Path> sourceDirPaths, final String zipFilePath) throws IOException {
        Path p = Files.createFile(Paths.get(zipFilePath).normalize().toAbsolutePath());
        try (ZipOutputStream zs = new ZipOutputStream(Files.newOutputStream(p))) {
            for (val sourceDirPath : sourceDirPaths) {
                Path pp = sourceDirPath.toRealPath();
                try (val walk = Files.walk(pp)) {
                    walk.filter(path -> !Files.isDirectory(path))
                        .forEach(path -> {
                            String relativePath = pp.relativize(path.normalize().toAbsolutePath()).toString();
                            // in case we run on Windows
                            relativePath = relativePath.replace(System.getProperty("file.separator"), "/");
                            ZipEntry zipEntry = new ZipEntry(relativePath);
                            try {
                                zs.putNextEntry(zipEntry);
                                zs.write(Files.readAllBytes(path));
                                zs.closeEntry();
                            } catch (Exception e) {
                                log.error("Skipping file {} because of error: {}", path, e.getMessage());
                            }
                        });
                }
            }
        }
    }

    /**
     * Zip source directory to destination path.
     *
     * @param sourceDirPaths source dir paths
     * @param relativeRoot   The a shared parent of the sourceDirPaths that should be used for all entries.
     * @param zipFilePath    destination zip file
     * @throws IOException io exception
     */
    public static void pack(final List<Path> sourceDirPaths,
                            final Path relativeRoot,
                            final String zipFilePath) throws IOException {

        val files = getFilesInDirectories(sourceDirPaths);
        packFiles(files, relativeRoot, Paths.get(zipFilePath));
    }

    /**
     * Zip source directory to destination path.
     *
     * @param files        source file paths
     * @param relativeRoot The shared parent of the sourceDirPaths that should be used for all entries.
     * @param zipFilePath  destination zip file
     * @throws IOException io exception
     */
    public static void packFiles(final Collection<Path> files,
                                 final Path relativeRoot,
                                 final Path zipFilePath) throws IOException {
        val normalizedRoot = relativeRoot.toRealPath();
        val normalizedFiles = files.stream()
                                   .map(Path::toAbsolutePath)
                                   .map(Path::normalize)
                                   .collect(Collectors.toList());
        normalizedFiles.forEach(file -> {
            if (!file.startsWith(normalizedRoot)) {
                val msg = String.format("%s is not a parent directory of %s", normalizedRoot, file);
                throw new RuntimeException(msg);
            }
        });
        Path zipFile = Files.createFile(zipFilePath);
        try (ZipOutputStream zs = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            for (val file : normalizedFiles) {
                val relPath = normalizedRoot.relativize(file).normalize().toString();
                ZipEntry zipEntry = new ZipEntry(relPath);
                try {
                    zs.putNextEntry(zipEntry);
                    zs.write(Files.readAllBytes(file));
                    zs.closeEntry();
                } catch (Exception e) {
                    log.error("Skipping file {} because of error: {}", file, e.getMessage());
                }
            }
        }
    }

    /**
     * Get files under directory recursively.
     *
     * @param directories Root directory.
     * @return All files under the root directory.
     * @throws IOException If reading the file system fails.
     */
    public static List<Path> getFilesInDirectories(Collection<Path> directories) throws IOException {
        val files = new ArrayList<Path>();
        for (val directory : directories) {
            Path pp = directory.toRealPath();
            files.addAll(getFilesInDirectory(pp));
        }
        return files;
    }

    /**
     * Get files under directory recursively.
     *
     * @param directory Root directory.
     * @return All files under the root directory.
     * @throws IOException If reading the file system fails.
     */
    public static List<Path> getFilesInDirectory(final Path directory) throws IOException {
        if (directory == null || !directory.toFile().isDirectory()) {
            return Collections.emptyList();
        }
        try (val walk = Files.walk(directory.toRealPath())) {
            return walk.filter(path -> !Files.isDirectory(path)).collect(Collectors.toList());
        }
    }

    /**
     * private construct.
     */
    private ZipUtils() {
    }

}
