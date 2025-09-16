package org.aocompressor;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.basic.BasicProgressBarUI;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.Objects;

/**
 * GUI for compressing and decompressing Argentum Online resources.
 * <p>
 * TODO Show message in log if is a file compressed in VB6
 * TODO Implement encryption?
 */

public class App extends JFrame {

    private final Compressor compressor = new Compressor();
    private Logger logger;
    private JButton compressButton, decompressButton;
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
        JTextPane logPane = new JTextPane();
        logPane.setEditable(false);
        logPane.setFont(new Font("Consolas", Font.PLAIN, 11));
        logPane.setMargin(new Insets(5, 5, 0, 0));

        logger = new Logger(logPane);

        JScrollPane scrollPane = new JScrollPane(logPane);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

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
        File folder = chooseFolder("Select folder to compress");
        if (folder == null) return;

        // 2) Specified .ao file
        String file = chooseAOToSave();
        if (file == null) return;

        logger.log("Starting compression of '" + folder.getName() + "' folder...");

        new FileOperationWorker(
                () -> {
                    var result = compressor.compress(folder, file);
                    if (result.success() && result.filesProcessed() > 0) logCompressionStats(folder, file);
                    return result;
                },
                logger,
                progressBar,
                () -> setUIEnabled(false),
                () -> setUIEnabled(true),
                "Compressed",
                file
        ).execute();

    }

    private void logCompressionStats(File folder, String file) {
        try {
            Path filePath = Path.of(file);
            long originalSize = Utils.folderSize(folder.getAbsolutePath());
            long compressedSize = Files.size(filePath);
            double ratio = (1.0 - (double) compressedSize / originalSize) * 100.0;

            logger.log(String.format("%s → %s (%.1f%% reduction)", Utils.formatFileSize(originalSize), Utils.formatFileSize(compressedSize), ratio));

            byte[] bytes = Files.readAllBytes(filePath);
            logger.log("SHA-256: " + Utils.sha256Hex(bytes));
        } catch (Exception e) {
            logger.warn("Could not calculate compression stats: " + e.getMessage());
        }
    }

    private void decompress(ActionEvent e) {
        // 1) Select .ao file
        File file = chooseAOToOpen();
        if (file == null) return;

        // 2) Select a destination folder
        File folder = chooseFolder("Select destination folder");
        if (folder == null) return;

        // Calculate the effective decompression path
        String baseName = file.getName();
        int dot = baseName.lastIndexOf('.');
        if (dot > 0) baseName = baseName.substring(0, dot);
        String effectiveTargetPath = folder.getAbsolutePath() + File.separator + baseName + "-descompressed";

        logger.log("Starting decompression of '" + file.getName() + "' file...");

        new FileOperationWorker(
                () -> {
                    var result = compressor.decompress(file.getAbsolutePath(), folder.getAbsolutePath(), logger::log);
                    if (result.success() && result.filesProcessed() > 0) logDecompressionStats(file);
                    return result;
                },
                logger,
                progressBar,
                () -> setUIEnabled(false),
                () -> setUIEnabled(true),
                "Decompressed",
                effectiveTargetPath
        ).execute();
    }

    private void logDecompressionStats(File file) {
        try {
            logger.log("Last Modified: " + new Date(file.lastModified()));
            byte[] bytes = Files.readAllBytes(file.toPath());
            logger.log("SHA-256: " + Utils.sha256Hex(bytes));
        } catch (Exception e) {
            logger.warn("Could not read file stats: " + e.getMessage());
        }
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

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
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

    private File chooseFolder(String title) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setFileSelectionMode(javax.swing.JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) return null;

        File folder = chooser.getSelectedFile();
        if (folder == null || !folder.exists() || !folder.isDirectory()) {
            showError("The folder '" + (folder == null ? "" : folder) + "' does not exist.");
            return null;
        }
        return folder;
    }

}