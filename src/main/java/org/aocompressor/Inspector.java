package org.aocompressor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Utility class for inspecting .ao (Argentum Online) compressed files.
 * <p>
 * Provides detailed information about the archive and its contents.
 * <p>
 * TODO Mostrar log si es un archivo comprimido en vb6
 */

public class Inspector {

    /**
     * Inspects an .ao file and reports detailed information.
     *
     * @param file   the .ao file to inspect
     * @param logger callback interface for receiving log messages
     */
    public static void inspect(File file, BiConsumer<String, MessageType> logger) {
        try (ZipFile zipFile = new ZipFile(file, StandardCharsets.UTF_8)) {
            inspectBasicInfo(file, logger);
            inspectArchiveContents(zipFile, logger);
        } catch (IOException e) {
            logger.accept("Error inspecting file: " + e.getMessage(), MessageType.ERROR);
        }
    }

    /**
     * Inspects basic file information.
     */
    private static void inspectBasicInfo(File file, BiConsumer<String, MessageType> logger) {
        logger.accept("=== FILE INSPECTION ===", MessageType.INFO);
        logger.accept("File: " + file.getName(), MessageType.INFO);
        logger.accept("Path: " + file.getAbsolutePath(), MessageType.INFO);
        logger.accept("Size: " + Utils.formatFileSize(file.length()), MessageType.INFO);
        logger.accept("Last Modified: " + new Date(file.lastModified()), MessageType.INFO);
        logger.accept("", MessageType.INFO);

        // Calculate SHA-256 of the entire file
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            logger.accept("File SHA-256: " + sha256Hex(bytes), MessageType.INFO);
            logger.accept("", MessageType.INFO);
        } catch (Exception e) {
            logger.accept("Could not calculate file SHA-256: " + e.getMessage(), MessageType.ERROR);
            logger.accept("", MessageType.INFO);
        }
    }

    /**
     * Inspects the contents of the archive
     */
    private static void inspectArchiveContents(ZipFile zipFile, BiConsumer<String, MessageType> logger) {
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        List<ZipEntry> fileEntries = new ArrayList<>();

        // Collect all file entries (skip directories)
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (!entry.isDirectory()) fileEntries.add(entry);
        }

        logger.accept("=== ARCHIVE CONTENTS ===", MessageType.INFO);
        logger.accept("Total files: " + fileEntries.size(), MessageType.INFO);
        logger.accept("", MessageType.INFO);

        if (fileEntries.isEmpty()) {
            logger.accept("No files found in the archive.", MessageType.WARN);
            return;
        }

        // Inspect each file entry
        for (ZipEntry entry : fileEntries) {
            try {
                inspectZipEntry(zipFile, entry, logger);
                logger.accept("", MessageType.INFO); // Blank line between files
            } catch (Exception e) {
                logger.accept("Error inspecting '" + entry.getName() + "': " + e.getMessage(), MessageType.ERROR);
                logger.accept("", MessageType.INFO);
            }
        }
    }

    /**
     * Inspects an individual ZIP entry.
     */
    private static void inspectZipEntry(ZipFile zipFile, ZipEntry entry, BiConsumer<String, MessageType> logger) throws Exception {
        logger.accept("--- " + entry.getName() + " ---", MessageType.INFO);
        logger.accept("Compressed size: " + Utils.formatFileSize(entry.getCompressedSize()), MessageType.INFO);
        logger.accept("Uncompressed size: " + Utils.formatFileSize(entry.getSize()), MessageType.INFO);

        if (entry.getSize() > 0) {
            double compressionRatio = (1.0 - (double) entry.getCompressedSize() / entry.getSize()) * 100;
            logger.accept("Compression ratio: " + String.format("%.1f%%", compressionRatio), MessageType.INFO);
        }

        // Read file data for analysis
        try (InputStream is = zipFile.getInputStream(entry)) {
            byte[] bytes = is.readAllBytes();

            if (bytes.length > 0) {
                logger.accept("SHA-256: " + sha256Hex(bytes), MessageType.INFO);
                logger.accept("Magic signature: " + firstBytesHex(bytes, 16), MessageType.INFO);
                logger.accept("Detected type: " + detectType(bytes), MessageType.INFO);
            } else logger.accept("File is empty.", MessageType.WARN);

        }
    }

    /**
     * Calculates SHA-256 hash of a byte array.
     */
    private static String sha256Hex(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(md.digest(data));
    }

    /**
     * Gets hexadecimal representation of first n bytes.
     */
    private static String firstBytesHex(byte[] data, int n) {
        int len = Math.min(n, data.length);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++)
            sb.append(String.format("%02X ", data[i]));
        return sb.toString().trim();
    }

    /**
     * Detects a file type based on a magic signature.
     * <p>
     * TODO No seria mejor obtener la extension del archivo?
     */
    private static String detectType(byte[] data) {
        if (data.length >= 4) {
            // PNG
            if (data.length >= 8 && data[0] == (byte) 0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47 && data[4] == 0x0D && data[5] == 0x0A && data[6] == 0x1A && data[7] == 0x0A)
                return "PNG";
            // JPEG
            if (data[0] == (byte) 0xFF && data[1] == (byte) 0xD8) return "JPEG";
            // WAV/RIFF
            if (data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F') return "RIFF (possible WAV)";
            // ZIP
            if (data[0] == 0x50 && data[1] == 0x4B && data[2] == 0x03 && data[3] == 0x04) return "ZIP";
            // BMP
            if (data[0] == 'B' && data[1] == 'M') return "BMP";
        }
        return "Unknown";
    }

}