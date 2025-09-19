package org.aocompressor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class Compressor {

    public Result compress(File sourceDirStr, String targetZipStr) {
        Path sourceDir = Paths.get(sourceDirStr.getAbsolutePath());
        Path targetZip = Paths.get(targetZipStr);

        try {
            if (!hasFiles(sourceDir)) return Result.success(0, "No files to compress.");

            int filesProcessed = compressDirectoryToZip(sourceDir, targetZip);
            return Result.success(filesProcessed, "Compression successfully!");

        } catch (IOException e) {
            deletePath(targetZip);
            return Result.failure("Compression failed!\n" + e.getMessage()); // TODO En que momento se muestra este mensaje?
        }
    }

    public Result decompress(String sourceZipStr, String targetDirStr, Consumer<String> logger) {
        Path sourceZip = Paths.get(sourceZipStr);
        Path targetDir = Paths.get(targetDirStr);

        try {
            // Create target directory based on source zip name
            targetDir = targetDir.resolve(Utils.getFileName(sourceZip) + "-decompressed");

            Files.createDirectories(targetDir);

            int filesProcessed = decompressZipToDirectory(sourceZip, targetDir, logger);
            return Result.success(filesProcessed, "Decompression successfully!");

        } catch (IOException e) {
            deletePath(targetDir);
            return Result.failure("Decompression failed!\n" + e.getMessage());
        }
    }

    private int compressDirectoryToZip(Path sourceDir, Path targetZip) throws IOException {
        int filesProcessed = 0;

        try (FileOutputStream fos = new FileOutputStream(targetZip.toFile());
             ZipOutputStream zos = new ZipOutputStream(fos, StandardCharsets.UTF_8);
             Stream<Path> paths = Files.walk(sourceDir)) {

            var files = paths.filter(Files::isRegularFile).iterator();

            while (files.hasNext()) {
                Path file = files.next();
                if (writeFileAsZipEntry(sourceDir, file, zos)) filesProcessed++;
                else throw new IOException();
            }
        }

        return filesProcessed;
    }

    private int decompressZipToDirectory(Path sourceZip, Path targetDir, Consumer<String> logger) throws IOException {
        int filesProcessed = 0;

        try (ZipFile zipFile = new ZipFile(sourceZip.toFile(), StandardCharsets.UTF_8)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                File targetFile = targetDir.resolve(entry.getName()).toFile();

                if (!isWithinDirectory(targetDir.toFile(), targetFile)) {
                    logger.accept("Skipping file (" + entry.getName() + ") outside folder.");
                    continue;
                }

                if (entry.isDirectory()) Files.createDirectories(targetFile.toPath());
                else if (extractZipEntryToFile(zipFile, entry, targetFile)) filesProcessed++;
            }
        }

        return filesProcessed;
    }


    private boolean writeFileAsZipEntry(Path sourceDir, Path file, ZipOutputStream zos) {
        String entryName = sourceDir.relativize(file).toString().replace('\\', '/');
        try {
            zos.putNextEntry(new ZipEntry(entryName));
            Files.copy(file, zos);
            zos.closeEntry();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private boolean extractZipEntryToFile(ZipFile zipFile, ZipEntry entry, File destFile) {
        try {
            Path parent = destFile.toPath().getParent();
            if (parent != null) Files.createDirectories(parent);
            try (InputStream is = zipFile.getInputStream(entry);
                 OutputStream os = Files.newOutputStream(destFile.toPath())) {
                is.transferTo(os);
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Validates that a targetFile is within the permitted directory (security against zip bombs).
     */
    private boolean isWithinDirectory(File targetDir, File targetFile) throws IOException {
        return targetFile.getCanonicalPath().startsWith(targetDir.getCanonicalPath() + File.separator) || targetFile.getCanonicalPath().equals(targetDir.getCanonicalPath());
    }

    private boolean hasFiles(Path sourceDir) throws IOException {
        try (Stream<Path> paths = Files.walk(sourceDir)) {
            return paths.anyMatch(Files::isRegularFile);
        }
    }

    private void deletePath(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Silently ignore deletion errors
        }
    }

    public record Result(int filesProcessed, boolean success, String message) {

        public static Result success(int filesProcessed, String message) {
            return new Result(filesProcessed, true, message);
        }

        public static Result failure(String message) {
            return new Result(-1, false, message);
        }

    }

}
