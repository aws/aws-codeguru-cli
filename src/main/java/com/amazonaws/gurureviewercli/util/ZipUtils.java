package com.amazonaws.gurureviewercli.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
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
    public static void pack(final List<String> sourceDirPaths, final String zipFilePath) throws IOException {
        Path p = Files.createFile(Paths.get(zipFilePath).normalize().toAbsolutePath());
        try (ZipOutputStream zs = new ZipOutputStream(Files.newOutputStream(p))) {
            for (val sourceDirPath : sourceDirPaths) {
                Path pp = Paths.get(sourceDirPath).normalize().toAbsolutePath();
                try (val walk = Files.walk(pp)) {
                    walk.filter(path -> !Files.isDirectory(path))
                        .forEach(path -> {
                            val normalizedPath = path.normalize().toAbsolutePath();
                            val relpath = pp.relativize(normalizedPath).toString();
                            ZipEntry zipEntry = new ZipEntry(relpath);
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
     * @param relativeRoot The a shared parent of the sourceDirPaths that should be used for all entries.
     * @param zipFilePath    destination zip file
     * @throws IOException io exception
     */
    public static void pack(final List<String> sourceDirPaths,
                            final Path relativeRoot,
                            final String zipFilePath) throws IOException {
        sourceDirPaths.stream().forEach(p -> {
            val child = Paths.get(p).toAbsolutePath().normalize();
            val parent = relativeRoot.toAbsolutePath().normalize();
            if (!child.startsWith(parent)) {
                val msg = String.format("Folder %s is not a subfolder of %s", child, parent);
                throw new RuntimeException(msg);
            }
        });
        Path p = Files.createFile(Paths.get(zipFilePath));
        try (ZipOutputStream zs = new ZipOutputStream(Files.newOutputStream(p))) {
            for (val sourceDirPath : sourceDirPaths) {
                Path pp = Paths.get(sourceDirPath).normalize().toAbsolutePath();
                try (val walk = Files.walk(pp)) {
                    walk.filter(path -> !Files.isDirectory(path))
                        .forEach(path -> {
                            val normalizedPath = path.normalize().toAbsolutePath();
                            val relPath = relativeRoot.toAbsolutePath()
                                                      .normalize()
                                                      .relativize(normalizedPath)
                                                      .normalize().toString();
                            ZipEntry zipEntry = new ZipEntry(relPath);
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
     * private construct.
     */
    private ZipUtils() {
    }

}
