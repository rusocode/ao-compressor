package org.aocompressor;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.basic.BasicProgressBarUI;
import javax.swing.text.BadLocationException;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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
 * TODO Show message in log if is a file compressed in VB6
 * TODO Implement encryption?
 */

public class App extends JFrame {

    private Logger logger;
    private JButton compressButton, decompressButton;
    private JTextPane logPane;
    private JProgressBar progressBar;

    public App() {
        initializeGUI();
    }

    public static void main(String[] args) {
        // main() runs on the "main thread", NOT on the EDT
        SwingUtilities.invokeLater(() -> new App().setVisible(true)); // We are now in EDT
    }

    private void initializeGUI() {
        setupWindow();
        setLayout(new BorderLayout());
        add(createTopPanel(), BorderLayout.NORTH);
        add(createCenterPanel(), BorderLayout.CENTER);
    }

    private void setupWindow() {
        setTitle("AO Compressor");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(500, 280);
        setLocationRelativeTo(null);
        setResizable(false);
        setIconImage(new ImageIcon(Objects.requireNonNull(getClass().getResource("/icon.png"))).getImage());
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Cannot set system look and feel: " + e.getMessage());
        }
    }

    private JPanel createTopPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 5));
        compressButton = createButton("Compress", this::compress);
        decompressButton = createButton("Decompress", this::decompress);
        buttonPanel.add(compressButton);
        buttonPanel.add(decompressButton);

        JLabel link = Utils.createLink("Source Code", "https://github.com/rusocode/ao-compressor");
        link.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 10));

        // Spacer on the left with the same size as the label on the right to balance the center
        panel.add(Box.createRigidArea(link.getPreferredSize()), BorderLayout.WEST);
        panel.add(buttonPanel);
        panel.add(link, BorderLayout.EAST);

        return panel;
    }

    private JButton createButton(String text, ActionListener listener) {
        JButton button = new JButton(text);
        button.setFocusable(false);
        button.addActionListener(listener);
        return button;
    }

    private JPanel createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // Log area
        logPane = new JTextPane();
        logPane.setEditable(false);
        logPane.setFont(new Font("Consolas", Font.PLAIN, 11));
        logPane.setMargin(new Insets(5, 5, 0, 0));

        logger = new Logger(logPane);

        JScrollPane scrollPane = new JScrollPane(logPane);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        scrollPane.setBorder(BorderFactory.createTitledBorder("LOGGER"));

        // Progress bar
        progressBar = createProgressBar();
        JPanel progressPanel = new JPanel(new BorderLayout());
        progressPanel.setBorder(BorderFactory.createEmptyBorder(0, 5, 5, 5));
        progressPanel.add(progressBar);

        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(progressPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JProgressBar createProgressBar() {
        JProgressBar bar = new JProgressBar(0, 100);
        bar.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        bar.setForeground(new Color(0x33BB4C));
        bar.setPreferredSize(new Dimension(0, 10));
        bar.setUI(new BasicProgressBarUI() {
            @Override
            protected void paintDeterminate(Graphics g, JComponent c) {
                Insets b = progressBar.getInsets();
                int w = progressBar.getWidth() - (b.left + b.right);
                int h = progressBar.getHeight() - (b.top + b.bottom);
                if (w <= 0 || h <= 0) return;

                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                    // Background (track)
                    g2.setColor(progressBar.getBackground());
                    g2.fillRect(b.left, b.top, w, h);

                    // Filling
                    int amountFull = getAmountFull(b, w, h);
                    int x = b.left, y = b.top, fw = amountFull, fh = h;
                    if (progressBar.getOrientation() == JProgressBar.VERTICAL) {
                        y = b.top + (h - amountFull);
                        fw = w;
                        fh = amountFull;
                    }
                    // If it is at maximum, it occupies the entire track
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
        return bar;
    }

    private void compress(ActionEvent e) {
        // 1) Select a folder to compress
        File folderSelected = chooseDirectory("Select folder to compress");
        if (folderSelected == null) return;

        // 2) Specified .ao file
        String aoPathString = chooseAOToSave();
        if (aoPathString == null) return;

        String folderPathString = folderSelected.getAbsolutePath();

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
                for (String message : chunks) appendLog(message);
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
        File aoSelected = chooseAOToOpen();
        if (aoSelected == null) return;

        String aoPathString = aoSelected.getAbsolutePath();

        // 2) Select a destination folder
        File folderSelected = chooseDirectory("Select destination folder");
        if (folderSelected == null) return;

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
                for (String message : chunks) appendLog(message);
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
        StyledDocument doc = logPane.getStyledDocument();
        String text = message + "\n";
        try {
            doc.insertString(doc.getLength(), text, logPane.getStyle(type.name()));
        } catch (BadLocationException ignored) {
        }
        logPane.setCaretPosition(doc.getLength());
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
     * If an error occurs during compression, the method deletes the output file.
     *
     * @param folderPathString name of the folder to compress
     * @param aoPathString     name of the resulting .ao archive file
     * @param logger           a Consumer instance for logging messages or errors compression
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

        // Check if the folder contains at least one regular file before creating the .ao file
        try (Stream<Path> stream = Files.walk(folderPath)) {
            boolean hasFiles = stream.anyMatch(Files::isRegularFile);
            if (!hasFiles) return 0; // Do not create the .ao file
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

    /**
     * Decompresses the specified {@code .ao} archive file into a target folder.
     * <p>
     * This method extracts all the files contained within the specified compressed archive to a destination folder. Determine the
     * folder from the given folder path, and the archive file's base name.
     *
     * @param aoPathString     path to the input {@code .ao} archive file to decompress
     * @param folderPathString path to the base folder for the decompressed files
     * @param logger           a Consumer instance for logging messages or errors during decompression
     * @return the amount files successfully decompressed, or -1 if an error occurs
     */
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

        // Gets the base name of the resource (.ao without extension) and always defines the subfolder "<baseName>-descompressed"
        String baseName = aoPath.getFileName().toString();
        int dot = baseName.lastIndexOf('.');
        if (dot > 0) baseName = baseName.substring(0, dot);

        Path targetRoot = Paths.get(folderPathString);
        Path targetFolder = targetRoot.resolve(baseName + "-descompressed");

        try {
            // Create the destination folder if it doesn't exist
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

                // Build the destination route
                File destFile = targetFolder.resolve(entry.getName()).toFile();

                // Ensure the file doesn't escape the target directory
                if (!isWithinDirectory(targetFolder.toFile(), destFile)) {
                    logger.accept("File '" + entry.getName() + "' is outside the target directory. Skipping");
                    continue;
                }

                if (entry.isDirectory()) {
                    // Create the directory
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

    /**
     * Compresses a single file into a zip archive.
     * <p>
     * This method adds the specified file to the given ZipOutputStream and logs any errors encountered during the compression
     * process.
     *
     * @param path   base directory path used to calculate the relative path for the zip entry
     * @param file   file to compress and add to the zip archive
     * @param zos    ZipOutputStream to write the compressed file to
     * @param logger a Consumer instance for logging messages or errors during compression
     * @return true if the method compresses the file and adds it to the archive or false
     */
    private boolean compressFile(Path path, Path file, ZipOutputStream zos, Consumer<String> logger) {
        String entryName = path.relativize(file).toString().replace('\\', '/');
        try {
            zos.putNextEntry(new ZipEntry(entryName));
            Files.copy(file, zos); // Delegate copying to the JDK
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
     * Decompresses a specific file from a zip archive to a specified destination.
     * <p>
     * This method extracts the content of a given {@link ZipEntry} within a {@link ZipFile} to the specified destination file.
     * Creates the destination file's parent directories if they don't exist.
     *
     * @param zipFile  ZipFile representing the zip archive containing the file to decompress
     * @param entry    ZipEntry within the zip file to extract it
     * @param destFile destination file that receives the decompressed content
     * @param logger   a Consumer instance for logging messages or errors during decompression
     * @return true if the method decompresses the file or false
     */
    private boolean decompressFile(ZipFile zipFile, ZipEntry entry, File destFile, Consumer<String> logger) {
        try {
            // Create parent directories if they don't exist (idempotent)
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

            // Extract the file using NIO and transferTo
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
     * Validates that a file is within the permitted directory (security against zip bombs).
     */
    private boolean isWithinDirectory(File parentDir, File file) throws IOException {
        return file.getCanonicalPath().startsWith(parentDir.getCanonicalPath() + File.separator) || file.getCanonicalPath().equals(parentDir.getCanonicalPath());
    }

    private File chooseAOToOpen() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select .ao file");
        chooser.setFileFilter(new FileNameExtensionFilter("AO files (*.ao)", "ao"));
        chooser.setAcceptAllFileFilterUsed(false);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return null;

        File file = chooser.getSelectedFile();
        if (file == null || !file.exists() || !file.isFile() || !file.getName().toLowerCase().endsWith(".ao")) {
            showError("The file '" + (file == null ? "" : file) + "' does not exist.");
            return null;
        }
        return file;
    }

    private String chooseAOToSave() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Specified .ao file");
        chooser.setFileFilter(new FileNameExtensionFilter("AO files (*.ao)", "ao"));
        chooser.setAcceptAllFileFilterUsed(false);
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return null;

        String selected = chooser.getSelectedFile().getAbsolutePath();
        return selected.toLowerCase().endsWith(".ao") ? selected : selected + ".ao";
    }

    private File chooseDirectory(String title) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return null;

        File dir = chooser.getSelectedFile();
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            showError("The folder '" + (dir == null ? "" : dir) + "' does not exist.");
            return null;
        }
        return dir;
    }

    private enum MessageType {
        INFO, SUCCESS, WARN, ERROR
    }

}