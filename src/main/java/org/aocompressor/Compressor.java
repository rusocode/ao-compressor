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

    public Result compress(File folder, String file) {
        try {
            Path folderPath = Paths.get(folder.getAbsolutePath());
            Path filePath = Paths.get(file);
            if (isInvalidFolderPath(folderPath)) return new Result(-1, false, "Invalid folder path.");
            return performCompression(folderPath, filePath);
        } catch (Exception e) {
            return new Result(-1, false, "Compression failed!\n" + e.getMessage());
        }
    }

    public Result decompress(String file, String folder, Consumer<String> logger) {
        try {
            Path filePath = Paths.get(file);
            Path folderPath = Paths.get(folder);
            if (isInvalidFilePath(filePath)) return new Result(-1, false, "Invalid file path.");
            // Get the file name to use as the folder name
            String baseName = filePath.getFileName().toString();
            int dot = baseName.lastIndexOf('.');
            if (dot > 0) baseName = baseName.substring(0, dot);
            folderPath = folderPath.resolve(baseName + "-descompressed");
            return performDecompression(filePath, folderPath, logger);
        } catch (Exception e) {
            return new Result(-1, false, "Decompression failed!\n" + e.getMessage());
        }
    }

    private boolean isInvalidFolderPath(Path folderPath) {
        return !Files.exists(folderPath) || !Files.isDirectory(folderPath);
    }

    private boolean isInvalidFilePath(Path filePath) {
        return !Files.exists(filePath) || !Files.isRegularFile(filePath);
    }

    /**
     * Performs compression of all files within a specified folder into a given ZIP file.
     * <p>
     * This method checks if the folder contains files and compresses all regular files found within it into the provided ZIP
     * file. If compression fails for any file, the process aborts, the resulting ZIP file is deleted, and an error Result is
     * returned. If successful, the method returns a Result indicating the amount files compressed, a success status, and an
     * outcome message.
     *
     * @param folderPath path to the folder containing files to compress
     * @param filePath   path where the resulting ZIP file will be written
     * @return a Result object containing the amount files compressed, a success status, and a message describing the outcome
     */
    private Result performCompression(Path folderPath, Path filePath) {
        // Check if the folder has files first
        try (Stream<Path> stream = Files.walk(folderPath)) {
            if (stream.noneMatch(Files::isRegularFile)) return new Result(0, true, "No files to compress.");
        } catch (IOException e) {
            return new Result(-1, false, "Cannot read folder path.");
        }

        int fileCount = 0;
        try (FileOutputStream fos = new FileOutputStream(filePath.toFile());
             ZipOutputStream zos = new ZipOutputStream(fos, StandardCharsets.UTF_8);
             Stream<Path> fileStream = Files.walk(folderPath)) {

            var files = fileStream.filter(Files::isRegularFile).iterator();
            while (files.hasNext()) {
                Path file = files.next();
                if (compressFile(folderPath, file, zos)) fileCount++;
                else {
                    // Doesn't continue writing a potentially inconsistent ZIP file
                    Files.deleteIfExists(filePath);
                    return new Result(-1, false, "Failed to compress file '" + file + "'.");
                }
            }

        } catch (IOException e) {
            try {
                Files.deleteIfExists(filePath);
            } catch (IOException ignored) {
            }
            return new Result(-1, false, e.getMessage());
        }

        return new Result(fileCount, true, "Compression successfully!");
    }

    /**
     * Performs decompression of a given zip file into a specified folder.
     * <p>
     * The method creates the destination folder if it doesn't already exist. It processes each entry in the zip file, skipping
     * files that fall outside the target folder (for security reasons). Directories are created as needed, and files are
     * decompressed to their respective paths.
     *
     * @param filePath   path of the zip file to decompress
     * @param folderPath target folder where files will be decompressed
     * @param logger     a consumer to log messages during the decompression process
     * @return a Result object containing the amount files processed, success status, and a message describing the outcome of the
     * decompression
     */
    private Result performDecompression(Path filePath, Path folderPath, Consumer<String> logger) {
        try {
            Files.createDirectories(folderPath);
        } catch (IOException e) {
            return new Result(-1, false, "Cannot create folder path.");
        }

        int fileCount = 0;
        try (ZipFile zipFile = new ZipFile(filePath.toFile(), StandardCharsets.UTF_8)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                File destFile = folderPath.resolve(entry.getName()).toFile();

                if (!isWithinDirectory(folderPath.toFile(), destFile)) {
                    logger.accept("Skipping file '" + entry.getName() + "' outside folder.");
                    continue;
                }

                if (entry.isDirectory()) {
                    try {
                        Files.createDirectories(destFile.toPath());
                    } catch (IOException e) {
                        logger.accept("Could not create directory '" + destFile.getName() + "'.");
                    }
                } else if (decompressFile(zipFile, entry, destFile)) fileCount++;

            }

        } catch (ZipException e) {
            try {
                Files.deleteIfExists(folderPath);
            } catch (IOException ignored) {
            }
            return new Result(-1, false, "Invalid zip file: " + e.getMessage());
        } catch (IOException e) {
            return new Result(-1, false, "Cannot read zip file.\n" + e.getMessage());
        }

        return new Result(fileCount, true, "Decompression successfully!");
    }

    /**
     * Compresses a single file into a zip archive.
     * <p>
     * This method adds the specified file to the given ZipOutputStream and logs any errors encountered during the compression
     * process.
     *
     * @param folderPath folder path used to calculate the relative path for the zip entry
     * @param file       file to compress and add to the zip archive
     * @param zos        ZipOutputStream to write the compressed file to
     * @return true if the method compresses the file and adds it to the archive or false
     */
    private boolean compressFile(Path folderPath, Path file, ZipOutputStream zos) {
        String entryName = folderPath.relativize(file).toString().replace('\\', '/');
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
            Path parent = destFile.toPath().getParent();
            if (parent != null) Files.createDirectories(parent);
            try (InputStream is = zipFile.getInputStream(entry); OutputStream os = Files.newOutputStream(destFile.toPath())) {
                is.transferTo(os);
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Validates that a file is within the permitted directory (security against zip bombs).
     */
    private boolean isWithinDirectory(File parentDir, File file) throws IOException {
        return file.getCanonicalPath().startsWith(parentDir.getCanonicalPath() + File.separator) || file.getCanonicalPath().equals(parentDir.getCanonicalPath());
    }

    public record Result(int filesProcessed, boolean success, String message) {
    }

}
