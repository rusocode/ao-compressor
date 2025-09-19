package org.aocompressor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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

    public Result compress(File sourceDir, String targetZip) {
        Path sourcePath = sourceDir.toPath();
        Path targetPath = Paths.get(targetZip);

        try {
            if (!hasFiles(sourcePath)) return Result.success(0, "No files to compress.");

            int filesProcessed = compressToZip(sourcePath, targetPath);
            return Result.success(filesProcessed, "Compression successful!");

        } catch (IOException e) {
            Utils.deletePath(targetPath);
            return Result.failure("Compression failed!\n" + e.getMessage()); // TODO En que momento se muestra este mensaje?
        }
    }

    public Result decompress(String sourceZip, String targetDir, Consumer<String> logger) {
        Path sourcePath = Paths.get(sourceZip);
        Path targetPath = Paths.get(targetDir).resolve(Utils.getFileName(sourcePath) + "-decompressed");

        try {
            Files.createDirectories(targetPath);
            int filesProcessed = decompressFromZip(sourcePath, targetPath, logger);
            return Result.success(filesProcessed, "Decompression successful!");

        } catch (IOException e) {
            Utils.deletePath(targetPath);
            return Result.failure("Decompression failed!\n" + e.getMessage());
        }
    }

    private int compressToZip(Path sourceDir, Path targetZip) throws IOException {
        int filesProcessed = 0;

        try (var fos = new FileOutputStream(targetZip.toFile());
             var zos = new ZipOutputStream(fos, StandardCharsets.UTF_8);
             var paths = Files.walk(sourceDir)) {

            var files = paths.filter(Files::isRegularFile).iterator();

            while (files.hasNext()) {
                Path file = files.next();
                if (addFileToZip(sourceDir, file, zos)) filesProcessed++;
                else throw new IOException();
            }
        }

        return filesProcessed;
    }

    private int decompressFromZip(Path sourceZip, Path targetDir, Consumer<String> logger) throws IOException {
        int filesProcessed = 0;

        try (var zipFile = new ZipFile(sourceZip.toFile(), StandardCharsets.UTF_8)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                File targetFile = targetDir.resolve(entry.getName()).toFile();

                if (!isWithinDirectory(targetDir.toFile(), targetFile)) {
                    logger.accept("Skipping file outside target directory: " + entry.getName());
                    continue;
                }

                if (entry.isDirectory()) Files.createDirectories(targetFile.toPath());
                else if (extractFileFromZip(zipFile, entry, targetFile)) filesProcessed++;
            }
        }

        return filesProcessed;
    }


    private boolean addFileToZip(Path sourceDir, Path file, ZipOutputStream zos) {
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

    private boolean extractFileFromZip(ZipFile zipFile, ZipEntry entry, File destFile) {
        try {
            Path parent = destFile.toPath().getParent();
            if (parent != null) Files.createDirectories(parent);
            try (var is = zipFile.getInputStream(entry);
                 var os = Files.newOutputStream(destFile.toPath())) {
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
        String targetDirPath = targetDir.getCanonicalPath() + File.separator;
        String targetFilePath = targetFile.getCanonicalPath();
        return targetFilePath.startsWith(targetDirPath) || targetFilePath.equals(targetDir.getCanonicalPath());
    }

    private boolean hasFiles(Path sourceDir) throws IOException {
        try (Stream<Path> paths = Files.walk(sourceDir)) {
            return paths.anyMatch(Files::isRegularFile);
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
