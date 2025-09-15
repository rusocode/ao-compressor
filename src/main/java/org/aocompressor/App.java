package org.aocompressor;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.basic.BasicProgressBarUI;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * GUI for compressing and decompressing Argentum Online resources.
 * <p>
 * TODO Mostrar log si es un archivo comprimido en vb6
 */

public class App extends JFrame {

    private JButton compressButton, decompressButton;
    private JTextPane log;
    private JProgressBar progressBar;

    public App() {
        initializeGUI();
    }

    public static void main(String[] args) {
        // main() runs on the "main thread", NOT on the EDT
        SwingUtilities.invokeLater(() -> {
            try {
                // We are now in EDT
                new App().setVisible(true);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, "Error initializing the application: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void initializeGUI() {
        setTitle("Compressor");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(500, 280);
        setLocationRelativeTo(null);
        setResizable(false);
        setIconImage(new ImageIcon(Objects.requireNonNull(getClass().getResource("/icon.png"))).getImage());

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
            showError("Error setting the system look and feel: " + e.getMessage());
        }

        setLayout(new BorderLayout());

        add(createTopPanel(), BorderLayout.NORTH);
        add(createCenterPanel(), BorderLayout.CENTER);
    }

    private JPanel createTopPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JPanel buttonPanel = new JPanel(new FlowLayout());

        compressButton = new JButton("Compress");
        compressButton.setFocusable(false);
        compressButton.addActionListener(this::compress);
        buttonPanel.add(compressButton);

        decompressButton = new JButton("Decompress");
        decompressButton.setFocusable(false);
        decompressButton.addActionListener(this::decompress);
        buttonPanel.add(decompressButton);

        panel.add(buttonPanel);

        return panel;
    }

    private JPanel createCenterPanel() {
        JPanel bottomPanel = new JPanel(new BorderLayout());

        // Log area
        log = new JTextPane();
        log.setEditable(false);
        // log.setFocusable(false); // Disable text selection
        log.setFont(new Font("Consolas", Font.PLAIN, 11));
        log.setMargin(new Insets(5, 5, 0, 0));

        // Estilos de color por tipo
        Style base = log.addStyle("BASE", null);
        Style info = log.addStyle(MessageType.INFO.name(), base);
        StyleConstants.setForeground(info, new Color(0x444444));
        Style success = log.addStyle(MessageType.SUCCESS.name(), base);
        StyleConstants.setForeground(success, new Color(0x33BB4C));
        Style warn = log.addStyle(MessageType.WARN.name(), base);
        StyleConstants.setForeground(warn, new Color(0xF1A60F));
        Style error = log.addStyle(MessageType.ERROR.name(), base);
        StyleConstants.setForeground(error, new Color(0xCC3333));

        JScrollPane scrollPane = new JScrollPane(log);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        scrollPane.setBorder(BorderFactory.createTitledBorder("LOGGER"));
        bottomPanel.add(scrollPane, BorderLayout.CENTER);

        // Progress bar
        JPanel progressPanel = new JPanel(new BorderLayout());
        progressPanel.setBorder(BorderFactory.createEmptyBorder(0, 5, 5, 5));
        progressBar = new JProgressBar(0, 100);
        progressBar.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        progressBar.setForeground(new Color(0x33BB4C));
        progressBar.setPreferredSize(new Dimension(0, 10));
        progressBar.setUI(new BasicProgressBarUI() {
            @Override
            protected void paintDeterminate(Graphics g, JComponent c) {
                Insets b = progressBar.getInsets();
                int w = progressBar.getWidth() - (b.left + b.right);
                int h = progressBar.getHeight() - (b.top + b.bottom);
                if (w <= 0 || h <= 0) return;

                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                    // Fondo (track)
                    g2.setColor(progressBar.getBackground());
                    g2.fillRect(b.left, b.top, w, h);

                    // Relleno
                    int amountFull = getAmountFull(b, w, h);
                    int x = b.left, y = b.top, fw = amountFull, fh = h;
                    if (progressBar.getOrientation() == JProgressBar.VERTICAL) {
                        y = b.top + (h - amountFull);
                        fw = w;
                        fh = amountFull;
                    }
                    // Si esta al maximo, ocupa todo el track
                    if (progressBar.getValue() >= progressBar.getMaximum()) {
                        x = b.left;
                        y = b.top;
                        fw = w;
                        fh = h;
                    }

                    g2.setColor(progressBar.getForeground());
                    g2.fillRect(x, y, fw, fh);

                    if (progressBar.isStringPainted()) paintString(g2, b.left, b.top, w, h, amountFull, b);

                } finally {
                    g2.dispose();
                }
            }
        });

        progressPanel.add(progressBar, BorderLayout.CENTER);

        bottomPanel.add(progressPanel, BorderLayout.SOUTH);

        return bottomPanel;
    }

    private void compress(ActionEvent e) {
        // 1) Select a folder to compress
        JFileChooser folderChooser = new JFileChooser();
        folderChooser.setDialogTitle("Select folder to compress");
        folderChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        folderChooser.setAcceptAllFileFilterUsed(false);
        if (folderChooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File folderSelected = folderChooser.getSelectedFile();
        if (folderSelected == null || !folderSelected.exists() || !folderSelected.isDirectory()) {
            showError("The folder '" + (folderSelected == null ? "" : folderSelected) + "' does not exist.");
            return;
        }

        String folderPathString = folderSelected.getAbsolutePath();

        // 2) Specified .ao file
        JFileChooser aoChooser = new JFileChooser();
        aoChooser.setDialogTitle("Specified .ao file");
        aoChooser.setFileFilter(new FileNameExtensionFilter("AO files (*.ao)", "ao"));
        aoChooser.setAcceptAllFileFilterUsed(false);
        if (aoChooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        String aoSelected = aoChooser.getSelectedFile().getAbsolutePath();
        String aoPathString = aoSelected.toLowerCase().endsWith(".ao") ? aoSelected : aoSelected + ".ao";

        // Run compression in the background
        SwingWorker<Integer, String> worker = new SwingWorker<>() {

            /* Contains the logic for the heavy task (runs in the background).
             * <p>
             * Executes the compression task in the background, compressing files from the specified input folder into the
             * specified output file. While the task runs, the UI shows progress.
             *
             * @return the amount files successfully compressed
             * @throws Exception if an error occurs during the compression process */
            @Override
            protected Integer doInBackground() {
                publish("Starting compression of the '" + folderSelected.getName() + "' folder..."); // Send interim results (call process() method) to the EDT (Event Dispatch Thread)
                setUIEnabled(false);
                // Used to set the progress bar to indeterminate mode (marquee animation without an actual percentage)
                progressBar.setIndeterminate(true);

                long start = System.nanoTime();
                int filesCompressed = compress(folderPathString, aoPathString, this::publish);
                long time = (System.nanoTime() - start) / 1_000_000;

                if (filesCompressed > 0) {
                    publish("Compressed " + filesCompressed + " file" + (filesCompressed > 1 ? "s" : "") + " to '" + aoPathString + "' in " + time + "ms");

                    long uncompressedBytes = Utils.folderSize(folderPathString);
                    long compressedBytes = 0;

                    Path aoPath = null;
                    // Tamaño del archivo .ao ya generado
                    try {
                        aoPath = Paths.get(aoPathString);
                        if (Files.exists(aoPath)) compressedBytes = Files.size(aoPath);
                    } catch (IOException e) {
                        publish("Could not read compressed archive size: " + e.getMessage());
                    }

                    double reductionPct = (uncompressedBytes > 0) ? (1.0 - (double) compressedBytes / uncompressedBytes) * 100.0 : 0.0;

                    publish(Utils.formatFileSize(uncompressedBytes) + " → " + Utils.formatFileSize(compressedBytes) + " (" + String.format("%.1f%%", reductionPct) + " compressed)");

                    try {
                        byte[] bytes = Files.readAllBytes(aoPath);
                        publish("SHA-256: " + Utils.sha256Hex(bytes));
                    } catch (IOException ex) {
                        publish("Could not read compressed archive: " + ex.getMessage());
                    } catch (Exception e) {
                        publish("Could not calculate SHA-256: " + e.getMessage());
                    }
                }

                return filesCompressed;
            }

            /* Processes intermediate results (a list of log messages) in the EDT (Event Dispatch Thread) and appends them to the
             * log.
             *
             * @param chunks the list of log messages to process */
            @Override
            protected void process(List<String> chunks) {
                for (String message : chunks)
                    appendLog(message);
            }

            /* Finalizes the background compression task and updates the UI accordingly.
             *
             * The Event Dispatch Thread (EDT) calls this method once the worker thread completes its background execution. Based
             * on the result of the task, the method updates the progress bar and logs and restores the state of the UI
             * components. It also handles any exceptions that occurred during the task execution. */
            @Override
            protected void done() {
                try {
                    // Get the result of the compression task (files compressed)
                    int result = get();
                    progressBar.setIndeterminate(false);
                    // If compression successful
                    if (result > 0) {
                        progressBar.setValue(100);
                        appendLog("Compression successfully!", MessageType.SUCCESS);
                    } else if (result == 0) { // If no files found
                        progressBar.setValue(0);
                        appendLog("No files found to compress.", MessageType.WARN);
                    } else {
                        progressBar.setValue(0); // If compression fails
                        appendLog("Compression failed!", MessageType.ERROR);
                    }
                } catch (Exception e) {
                    appendLog("Unexpected error: " + e.getMessage(), MessageType.ERROR);
                } finally {
                    setUIEnabled(true);
                    appendLog(""); // Line break
                }
            }

        };

        worker.execute();
    }

    private void decompress(ActionEvent e) {
        // 1) Select .ao file
        JFileChooser aoChooser = new JFileChooser();
        aoChooser.setDialogTitle("Select .ao file");
        aoChooser.setFileFilter(new FileNameExtensionFilter("AO files (*.ao)", "ao"));
        aoChooser.setAcceptAllFileFilterUsed(false);
        if (aoChooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File aoSelected = aoChooser.getSelectedFile();
        if (aoSelected == null || !aoSelected.exists() || !aoSelected.isFile() || !aoSelected.getName().toLowerCase().endsWith(".ao")) {
            showError("The file '" + (aoSelected == null ? "" : aoSelected) + "' does not exist.");
            return;
        }

        String aoPathString = aoSelected.getAbsolutePath();

        // 2) Select a destination folder
        JFileChooser folderChooser = new JFileChooser();
        folderChooser.setDialogTitle("Select destination folder");
        folderChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        folderChooser.setAcceptAllFileFilterUsed(false);
        if (folderChooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File folderSelected = folderChooser.getSelectedFile();
        if (folderSelected == null || !folderSelected.exists() || !folderSelected.isDirectory()) {
            showError("The folder '" + (folderSelected == null ? "" : folderSelected) + "' does not exist.");
            return;
        }

        String folderPathString = folderSelected.getAbsolutePath();

        // Run decompression in the background
        SwingWorker<Integer, String> worker = new SwingWorker<>() {
            @Override
            protected Integer doInBackground() {
                publish("Starting decompression of the '" + aoSelected.getName() + "' file...");
                setUIEnabled(false);
                progressBar.setIndeterminate(true);

                long start = System.nanoTime();
                int filesDecompressed = decompress(aoPathString, folderPathString, this::publish);
                long time = (System.nanoTime() - start) / 1_000_000;

                if (filesDecompressed > 0) {

                    // Calculate the actual destination, always <baseName>-uncompressed within the chosen folder
                    String baseName = aoSelected.getName();
                    int dot = baseName.lastIndexOf('.');
                    if (dot > 0) baseName = baseName.substring(0, dot);
                    Path effectiveTarget = Paths.get(folderPathString).resolve(baseName + "-descompressed");

                    publish("Decompressed " + filesDecompressed + " file" + (filesDecompressed > 1 ? "s" : "") + " to '" + effectiveTarget + "' in " + time + "ms");

                    publish("Last Modified: " + new Date(aoSelected.lastModified()));
                    try {
                        publish("SHA-256: " + Utils.sha256Hex(Files.readAllBytes(aoSelected.toPath())));
                    } catch (Exception e) {
                        publish("Could not read compressed archive: " + e.getMessage());
                    }
                }

                return filesDecompressed;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String message : chunks)
                    appendLog(message);
            }

            @Override
            protected void done() {
                try {
                    int result = get();
                    progressBar.setIndeterminate(false);
                    if (result > 0) {
                        progressBar.setValue(100);
                        appendLog("Decompressed successfully!", MessageType.SUCCESS);
                    } else if (result == 0) {
                        progressBar.setValue(0);
                        appendLog("No files found to decompress.", MessageType.WARN);
                    } else {
                        progressBar.setValue(0);
                        appendLog("Decompression failed!", MessageType.ERROR);
                    }
                } catch (Exception e) {
                    appendLog("Unexpected error: " + e.getMessage(), MessageType.ERROR);
                } finally {
                    setUIEnabled(true);
                    appendLog(""); // Line break
                }
            }

        };

        worker.execute();
    }

    /**
     * Enables or disables the UI components when the worker thread runs or not.
     *
     * @param enabled true to enable the UI components, false to disable them
     */
    private void setUIEnabled(boolean enabled) {
        compressButton.setEnabled(enabled);
        decompressButton.setEnabled(enabled);
    }

    private void appendLog(String message) {
        appendLog(message, MessageType.INFO);
    }

    private void appendLog(String message, MessageType type) {
        if (message == null) message = "";
        StyledDocument doc = log.getStyledDocument();
        String text = message + "\n";
        try {
            doc.insertString(doc.getLength(), text, log.getStyle(type.name()));
        } catch (BadLocationException ignored) {
        }
        log.setCaretPosition(doc.getLength());
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

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
    private int compress(String folderPathString, String aoPathString, Consumer<String> logger) {
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

        // Verifica si la carpeta contiene al menos un archivo regular antes de crear el .ao
        try (Stream<Path> stream = Files.walk(folderPath)) {
            boolean hasFiles = stream.anyMatch(Files::isRegularFile);
            if (!hasFiles) return 0; // No crea el archivo .ao
        } catch (IOException e) {
            logger.accept("Error walking directory structure. " + e.getMessage());
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

    private int decompress(String aoPathString, String folderPathString, Consumer<String> logger) {
        if (aoPathString == null || aoPathString.isBlank() || folderPathString == null || folderPathString.isBlank()) {
            logger.accept("aoPathString or folderPathString cannot be null, empty or blank.");
            return -1;
        }

        Path aoPath = Paths.get(aoPathString);
        if (!Files.exists(aoPath) || !Files.isRegularFile(aoPath)) {
            logger.accept("ao file '" + aoPath.getFileName() + "' does not exist.");
            return -1;
        }

        // Obtiene el nombre base del recurso (.ao sin extensión) y define siempre la subcarpeta "<baseName>-descompressed"
        String baseName = aoPath.getFileName().toString();
        int dot = baseName.lastIndexOf('.');
        if (dot > 0) baseName = baseName.substring(0, dot);

        Path targetRoot = Paths.get(folderPathString);
        Path targetFolder = targetRoot.resolve(baseName + "-descompressed");

        try {
            // Crea la carpeta destino si no existe
            Files.createDirectories(targetFolder);
        } catch (IOException e) {
            logger.accept("Could not create folder '" + targetFolder.getFileName() + "'.");
            return -1;
        }

        if (!Files.isDirectory(targetFolder)) {
            logger.accept("'" + targetFolder.getFileName() + "' is not a folder.");
            return -1;
        }

        int fileCount = 0;

        try (ZipFile zipFile = new ZipFile(aoPath.toFile(), StandardCharsets.UTF_8)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();

                // Construye la ruta de destino
                File destFile = targetFolder.resolve(entry.getName()).toFile();

                // Se asegura de que el archivo no escape del directorio destino
                if (!isWithinDirectory(targetFolder.toFile(), destFile)) {
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

    private boolean compressFile(Path path, Path file, ZipOutputStream zos, Consumer<String> logger) {
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
    private boolean decompressFile(ZipFile zipFile, ZipEntry entry, File destFile, Consumer<String> logger) {
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
    private boolean isWithinDirectory(File parentDir, File file) throws IOException {
        return file.getCanonicalPath().startsWith(parentDir.getCanonicalPath() + File.separator) || file.getCanonicalPath().equals(parentDir.getCanonicalPath());
    }

    private enum MessageType {
        INFO, SUCCESS, WARN, ERROR
    }

}