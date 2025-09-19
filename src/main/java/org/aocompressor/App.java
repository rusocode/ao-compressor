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
import java.util.List;
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
        setSize(500, 250);
        setLocationRelativeTo(null);
        setResizable(false);
        setIconImage(new ImageIcon(Objects.requireNonNull(getClass().getResource("/icon.png"))).getImage());
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            Utils.showError("Cannot set system look and feel.\n" + e.getMessage());
        }
    }

    private JPanel createTopPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // Buttons
        JPanel buttonPanel = new JPanel();
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
        panel.add(createLogPanel(), BorderLayout.CENTER);
        panel.add(createProgressPanel(), BorderLayout.SOUTH);
        return panel;
    }

    private JScrollPane createLogPanel() {
        JTextPane textPane = new JTextPane();
        textPane.setEditable(false);
        textPane.setOpaque(false);
        textPane.setFocusable(false);
        textPane.setFont(new Font("Consolas", Font.PLAIN, 11));
        textPane.setMargin(new Insets(5, 5, 0, 0));

        logger = new Logger(textPane);

        JScrollPane scrollPane = new JScrollPane(textPane);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        return scrollPane;
    }

    private JPanel createProgressPanel() {
        progressBar = createProgressBar();
        JPanel progressPanel = new JPanel(new BorderLayout());
        progressPanel.setBorder(BorderFactory.createEmptyBorder(0, 5, 5, 5));
        progressPanel.add(progressBar);
        return progressPanel;
    }

    private JProgressBar createProgressBar() {
        JProgressBar bar = new JProgressBar(0, 100);
        bar.setForeground(new Color(0x33BB4C));
        bar.setPreferredSize(new Dimension(0, 13));
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
        File sourceDir = chooseDirectory("Select folder to compress");
        if (sourceDir == null) return;

        // 2) Specified .ao (zip file) file
        String targetFile = chooseAOToSave();
        if (targetFile == null) return;

        logger.log("Starting compression of '" + sourceDir.getName() + "' folder...");

        // Executes a task to compress a folder with progress tracking, UI updates, and logging
        TaskRunner.run()
                .task(() -> compressor.compress(sourceDir, targetFile))
                .logger(logger)
                .progressBar(progressBar)
                .onStart(() -> setUIEnabled(false))
                .onFinish(() -> setUIEnabled(true))
                .operationType("Compressed")
                .targetPath(targetFile)
                .postLogs(() -> calculateCompression(sourceDir.toPath(), Path.of(targetFile)))
                .execute();

    }

    private void decompress(ActionEvent e) {
        // 1) Select .ao (zip file) file
        File sourceFile = chooseAOToOpen();
        if (sourceFile == null) return;

        // 2) Select a target folder
        File targetDir = chooseDirectory("Select target folder");
        if (targetDir == null) return;

        // Calculate the decompression path
        String targetPath = targetDir.toPath().resolve(Utils.getFileName(sourceFile.toPath()) + "-decompressed").toString();

        logger.log("Starting decompression of '" + sourceFile.getName() + "' file...");

        // Executes a task to decompress a file with progress tracking, UI updates, and logging
        TaskRunner.run()
                .task(() -> compressor.decompress(sourceFile.getAbsolutePath(), targetDir.getAbsolutePath(), logger::log))
                .logger(logger)
                .progressBar(progressBar)
                .onStart(() -> setUIEnabled(false))
                .onFinish(() -> setUIEnabled(true))
                .operationType("Decompressed")
                .targetPath(targetPath)
                .execute();
    }

    private List<String> calculateCompression(Path sourceDir, Path targetFile) {
        try {
            long directorySize = Utils.getDirectorySize(sourceDir);
            long compressedSize = Files.size(targetFile);
            double ratio = (1.0 - (double) compressedSize / directorySize) * 100.0;
            return List.of(String.format("%s → %s (%.1f%% compressed)",
                    Utils.formatFileSize(directorySize),
                    Utils.formatFileSize(compressedSize),
                    ratio));
        } catch (Exception e) {
            return List.of("Could not calculate compression.\n" + e.getMessage());
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

    private File chooseAOToOpen() {
        JFileChooser chooser = createAOChooser("Select .ao file");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return null;

        File file = chooser.getSelectedFile();
        if (file == null || !Files.isRegularFile(file.toPath()) || !file.getName().toLowerCase().endsWith(".ao")) {
            Utils.showError("The file '" + (file == null ? "" : file) + "' is invalid.");
            return null;
        }
        return file;
    }

    private String chooseAOToSave() {
        JFileChooser chooser = createAOChooser("Specified .ao file");
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return null;
        String path = chooser.getSelectedFile().getAbsolutePath();
        return path.toLowerCase().endsWith(".ao") ? path : path + ".ao";
    }

    private JFileChooser createAOChooser(String title) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setFileFilter(new FileNameExtensionFilter("AO files (*.ao)", "ao"));
        chooser.setAcceptAllFileFilterUsed(false);
        return chooser;
    }

    private File chooseDirectory(String title) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return null;

        File file = chooser.getSelectedFile();
        if (file == null || !file.isDirectory()) {
            Utils.showError("The folder '" + (file == null ? "" : file) + "' is invalid.");
            return null;
        }
        return file;
    }

}