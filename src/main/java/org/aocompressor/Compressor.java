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
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class Compressor {

    public Result compress(File sourceDir, String targetZip) {
        try {
            Path soureceDirPath = Paths.get(sourceDir.getAbsolutePath());
            // Perform a security validation here, even though chooseDirectory() already does it
            if (!isValidDirectory(soureceDirPath)) return Result.failure("Invalid source directory.");
            return createZipFromDirectory(soureceDirPath, Paths.get(targetZip));
        } catch (Exception e) {
            return Result.failure("Compression failed!\n" + e.getMessage());
        }
    }

    public Result decompress(String sourceZipStr, String targetDirStr, Consumer<String> logger) {
        try {
            Path sourceZip = Paths.get(sourceZipStr);
            Path targetDir = Paths.get(targetDirStr);

            if (!isValidFile(sourceZip)) return Result.failure("Invalid file path.");

            // Create target directory based on source zip name
            targetDir = targetDir.resolve(Utils.getZipName(sourceZip) + "-decompressed");

            return extractZipToDirectory(sourceZip, targetDir, logger);
        } catch (Exception e) {
            return Result.failure("Decompression failed!\n" + e.getMessage()); // TODO En que momento se muestra este mensaje?
        }
    }

    private boolean isValidDirectory(Path sourceDir) {
        return Files.exists(sourceDir) && Files.isDirectory(sourceDir);
    }

    private boolean isValidFile(Path sourceZip) {
        return Files.exists(sourceZip) && Files.isRegularFile(sourceZip);
    }

    private Result createZipFromDirectory(Path sourceDir, Path targetZip) {
        try {
            if (!hasFiles(sourceDir)) return Result.success(0, "No files to compress.");

            int fileCount = writeZipFromDirectory(sourceDir, targetZip);
            return Result.success(fileCount, "Compression successfully!");

        } catch (IOException e) {
            deleteZipQuietly(targetZip);
            return Result.failure("Cannot read source directory.\n" + e.getMessage()); // TODO En que momento se muestra este mensaje?
        }
    }

    private int writeZipFromDirectory(Path sourceDir, Path targetZip) throws IOException {
        int fileCount = 0;

        try (FileOutputStream fos = new FileOutputStream(targetZip.toFile());
             ZipOutputStream zos = new ZipOutputStream(fos, StandardCharsets.UTF_8);
             Stream<Path> fileStream = Files.walk(sourceDir)) {

            var files = fileStream.filter(Files::isRegularFile).iterator();
            while (files.hasNext()) {
                Path file = files.next();
                if (compressFile(sourceDir, file, zos)) fileCount++;
                else throw new IOException("Failed to compress file '" + file + "'.");
            }
        }

        return fileCount;
    }

    private boolean hasFiles(Path sourceDir) throws IOException {
        try (Stream<Path> stream = Files.walk(sourceDir)) {
            return stream.anyMatch(Files::isRegularFile);
        }
    }

    private Result extractZipToDirectory(Path sourceZip, Path targetDir, Consumer<String> logger) {
        try {
            Files.createDirectories(targetDir);
            int fileCount = extractZipEntries(sourceZip, targetDir, logger);
            return Result.success(fileCount, "Decompression successfully!");
        } catch (ZipException e) {
            deleteZipQuietly(targetDir);
            return Result.failure("Invalid zip file: " + e.getMessage());
        } catch (IOException e) {
            return Result.failure("Cannot create or read directories.\n" + e.getMessage()); // TODO En que momento se muestra este mensaje?
        }
    }

    private int extractZipEntries(Path sourceZip, Path targetDir, Consumer<String> logger) throws IOException {
        int fileCount = 0;

        try (ZipFile zipFile = new ZipFile(sourceZip.toFile(), StandardCharsets.UTF_8)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                File targetFile = targetDir.resolve(entry.getName()).toFile();

                if (!isWithinDirectory(targetDir.toFile(), targetFile)) {
                    logger.accept("Skipping file (" + entry.getName() + ") outside folder."); // TODO En que momento se muestra este mensaje?
                    continue;
                }

                if (entry.isDirectory()) createDirectoryQuietly(targetFile.toPath(), logger);
                else if (decompressFile(zipFile, entry, targetFile)) fileCount++;
            }
        }

        return fileCount;
    }

    /**
     * Compresses a single file into a zip archive.
     * <p>
     * This method adds the specified file to the given ZipOutputStream and logs any errors encountered during the compression
     * process.
     *
     * @param sourceDir folder path used to calculate the relative path for the zip entry
     * @param file      file to compress and add to the zip archive
     * @param zos       ZipOutputStream to write the compressed file to
     * @return true if the method compresses the file and adds it to the archive or false
     */
    private boolean compressFile(Path sourceDir, Path file, ZipOutputStream zos) {
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

    /**
     * Decompresses a specific file from a zip archive to a specified destination.
     * <p>
     * This method extracts the content of a given {@link ZipEntry} within a {@link ZipFile} to the specified destination file.
     * Creates the destination file's parent directories if they don't exist.
     *
     * @param zipFile  ZipFile representing the zip archive containing the file to decompress
     * @param entry    ZipEntry within the zip file to extract it
     * @param destFile destination file that receives the decompressed content
     * @return true if the method decompresses the file or false
     */
    private boolean decompressFile(ZipFile zipFile, ZipEntry entry, File destFile) {
        try {
            createParentDirectories(destFile.toPath());
            try (InputStream is = zipFile.getInputStream(entry);
                 OutputStream os = Files.newOutputStream(destFile.toPath())) {
                is.transferTo(os);
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void createParentDirectories(Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);
    }

    /**
     * Validates that a targetFile is within the permitted directory (security against zip bombs).
     */
    private boolean isWithinDirectory(File targetDir, File targetFile) throws IOException {
        return targetFile.getCanonicalPath().startsWith(targetDir.getCanonicalPath() + File.separator) || targetFile.getCanonicalPath().equals(targetDir.getCanonicalPath());
    }

    private void createDirectoryQuietly(Path targetFile, Consumer<String> logger) {
        try {
            Files.createDirectories(targetFile);
        } catch (IOException e) {
            logger.accept("Could not create directory '" + targetFile.getFileName() + "'."); // TODO En que momento se muestra este mensaje?
        }
    }

    private void deleteZipQuietly(Path targetZip) {
        try {
            Files.deleteIfExists(targetZip);
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
