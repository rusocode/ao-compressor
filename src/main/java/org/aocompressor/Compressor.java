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

/**
 * TODO Agrego nivel de compresion?
 */

public class Compressor {

    /**
     * Compresses the specified resource folder into an {@code .ao} archive file.
     * <p>
     * This method creates a compressed archive of all the regular files within the specified folder and saves it with the given
     * archive output name.
     * <p>
     * If an error occurs during the compression process, the output file is deleted.
     *
     * @param folderPathString name of the folder to compress
     * @param aoPathString     name of the resulting .ao archive file
     * @param logger           a Consumer instance for logging error messages
     * @return the amount files successfully compressed into the archive or -1 if an error occurs
     */
    public static int compress(String folderPathString, String aoPathString, Consumer<String> logger) {
        if (folderPathString == null || folderPathString.isBlank() || aoPathString == null || aoPathString.isBlank()) {
            logger.accept("folderPathString or aoPathString cannot be null, empty or blank.");
            return -1;
        }

        Path folderPath = Paths.get(folderPathString);
        Path aoPath = Paths.get(aoPathString);

        if (!Files.exists(folderPath)) {
            logger.accept("'" + folderPathString + "' does not exist");
            return -1;
        }

        if (!Files.isDirectory(folderPath)) {
            logger.accept("'" + folderPathString + "' is not a folder");
            return -1;
        }

        int fileCount = 0;
        boolean failure = false; // For communication if an error occurs during compression

        // Open the streams and postpone the return so that we can delete the file if it fails
        try (FileOutputStream fos = new FileOutputStream(aoPath.toFile());
             ZipOutputStream zos = new ZipOutputStream(fos, StandardCharsets.UTF_8)) {

            try (Stream<Path> stream = Files.walk(folderPath)) {
                var it = stream.filter(Files::isRegularFile).iterator();
                while (it.hasNext()) {
                    Path file = it.next();
                    if (compressFile(folderPath, file, zos, logger)) fileCount++;
                    else {
                        failure = true;
                        break; // Doesn't continue writing a potentially inconsistent ZIP file
                    }
                }
            } catch (IOException e) {
                logger.accept("Error walking directory structure. " + e.getMessage());
                failure = true;
            } catch (Exception e) {
                logger.accept("Unexpected error (" + e.getClass().getName() + "): " + e.getMessage());
                failure = true;
            }

        } catch (IOException e) {
            logger.accept("Error creating ao file. " + e.getMessage());
            failure = true;
        } catch (Exception e) {
            logger.accept("Unexpected error (" + e.getClass().getName() + "): " + e.getMessage());
            failure = true;
        }

        // Outside try-with-resources: the streams are already closed, so it safe to delete them if an error occurred
        if (failure) {
            try {
                Files.deleteIfExists(aoPath);
            } catch (IOException ignore) {
            }
            return -1;
        }

        return fileCount;

    }

    public static int decompress(String aoPathString, String folderPathString, Consumer<String> logger) {
        if (aoPathString == null || aoPathString.isBlank() || folderPathString == null || folderPathString.isBlank()) {
            logger.accept("aoPathString or folderPathString cannot be null, empty or blank.");
            return -1;
        }

        Path aoPath = Paths.get(aoPathString);
        if (!Files.exists(aoPath) || !Files.isRegularFile(aoPath)) {
            logger.accept("ao file '" + aoPath.getFileName() + "' does not exist.");
            return -1;
        }

        Path folderPath = Paths.get(folderPathString);
        try {
            // Crea la carpeta destino si no existe (idempotente)
            Files.createDirectories(folderPath);
        } catch (IOException e) {
            logger.accept("Could not create folder '" + folderPath.getFileName() + "'.");
            return -1;
        }

        if (!Files.isDirectory(folderPath)) {
            logger.accept("'" + folderPath.getFileName() + "' is not a folder.");
            return -1;
        }

        int fileCount = 0;

        try (ZipFile zipFile = new ZipFile(aoPath.toFile(), StandardCharsets.UTF_8)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();

                // Construye la ruta de destino
                File destFile = folderPath.resolve(entry.getName()).toFile();

                // Se asegura de que el archivo no escape del directorio destino
                if (!isWithinDirectory(folderPath.toFile(), destFile)) {
                    logger.accept("File '" + entry.getName() + "' is outside the target directory. Skipping");
                    continue;
                }

                if (entry.isDirectory()) {
                    // Crea el directorio
                    if (!destFile.exists() && !destFile.mkdirs())
                        logger.accept("Could not create directory '" + destFile.getName() + "'.");
                } else if (decompressFile(zipFile, entry, destFile, logger)) fileCount++;

            }

            return fileCount;

        } catch (IOException e) {
            logger.accept("Error reading ao file '" + aoPath.getFileName() + "': " + e.getMessage());
            return -1;
        } catch (Exception e) {
            logger.accept("Unexpected error (" + e.getClass().getName() + "): " + e.getMessage());
            return -1;
        }
    }

    private static boolean compressFile(Path path, Path file, ZipOutputStream zos, Consumer<String> logger) {
        String entryName = path.relativize(file).toString().replace('\\', '/');
        try {
            zos.putNextEntry(new ZipEntry(entryName));
            Files.copy(file, zos); // Simplified and efficient: delegate copying to the JDK
            zos.closeEntry();
            return true;
        } catch (IOException e) {
            logger.accept("Error compressing '" + file + "' - I/O error: " + e.getMessage());
            return false;
        } catch (Exception e) {
            logger.accept("Unexpected error (" + e.getClass().getName() + "): " + e.getMessage());
            return false;
        }
    }

    /**
     * Extrae un archivo individual del ZIP a la ubicacion destino.
     */
    private static boolean decompressFile(ZipFile zipFile, ZipEntry entry, File destFile, Consumer<String> logger) {
        try {
            // Crear directorios padre si no existen (idempotente)
            Path destPath = destFile.toPath();
            Path parent = destPath.getParent();
            if (parent != null) {
                try {
                    Files.createDirectories(parent);
                } catch (IOException e) {
                    logger.accept("Could not create parent directory: " + parent.toAbsolutePath());
                    return false;
                }
            }

            // Extrae el archivo utilizando NIO y transferTo
            try (InputStream is = zipFile.getInputStream(entry); OutputStream os = new BufferedOutputStream(Files.newOutputStream(destPath))) {
                is.transferTo(os);
            }

            return true;

        } catch (IOException e) {
            logger.accept("Error extracting file '" + entry.getName() + "': " + e.getMessage());
            return false;
        } catch (Exception e) {
            logger.accept("Unexpected error (" + e.getClass().getName() + "): " + e.getMessage());
            return false;
        }

    }

    /**
     * Valida que un archivo este dentro del directorio permitido (seguridad contra zip bombs).
     */
    private static boolean isWithinDirectory(File parentDir, File file) throws IOException {
        return file.getCanonicalPath().startsWith(parentDir.getCanonicalPath() + File.separator) || file.getCanonicalPath().equals(parentDir.getCanonicalPath());
    }

}
