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
import java.io.File;

/**
 * GUI for compressing and decompressing Argentum Online resources.
 */

public class App extends JFrame {

    private JButton compressButton, decompressButton, inspectButton;
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
                JOptionPane.showMessageDialog(null, "Error initializing the application: " + e.getMessage(), "Fatal Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void initializeGUI() {
        setTitle("Compressor");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(390, 280);
        setLocationRelativeTo(null);
        setResizable(false);

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
            throw new RuntimeException(e);
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

        inspectButton = new JButton("Inspect");
        inspectButton.setFocusable(false);
        inspectButton.addActionListener(this::inspect);
        buttonPanel.add(inspectButton);

        panel.add(buttonPanel);

        return panel;
    }

    private JPanel createCenterPanel() {
        JPanel bottomPanel = new JPanel(new BorderLayout());

        // Log area
        log = new JTextPane();
        log.setText("LOGGER\n\n");
        log.setEditable(false);
        // log.setFocusable(false); // Disable text selection
        log.setFont(new Font("Consolas", Font.PLAIN, 11));
        // log.setLineWrap(true); // Avoid horizontal scrolling
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
        // scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
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
                publish("Starting compression..."); // Send interim results (call process() method) to the EDT (Event Dispatch Thread)
                setUIEnabled(false);
                // Used to set the progress bar to indeterminate mode (marquee animation without an actual percentage)
                progressBar.setIndeterminate(true);

                long start = System.nanoTime();
                int filesCompressed = Compressor.compress(folderPathString, aoPathString, this::publish);
                long time = (System.nanoTime() - start) / 1_000_000;

                if (filesCompressed > 0)
                    publish("Compressed " + filesCompressed + " file" + (filesCompressed > 1 ? "s" : "") + " in " + time + " ms from '" + folderPathString + "' to '" + aoPathString + "'");

                return filesCompressed;
            }

            /* Processes intermediate results (a list of log messages) in the EDT (Event Dispatch Thread) and appends them to the
             * log.
             *
             * @param chunks the list of log messages to process */
            @Override
            protected void process(java.util.List<String> chunks) {
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
                publish("Starting decompression...");
                setUIEnabled(false);
                progressBar.setIndeterminate(true);

                long start = System.nanoTime();
                int filesDecompressed = Compressor.decompress(aoPathString, folderPathString, this::publish);
                long time = (System.nanoTime() - start) / 1_000_000;

                publish("Decompressed " + filesDecompressed + " file" + (filesDecompressed > 1 ? "s" : "") + " in " + time + " ms from '" + aoPathString + "' to '" + folderPathString + "'");
                return filesDecompressed;
            }


            @Override
            protected void process(java.util.List<String> chunks) {
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

    private void inspect(ActionEvent actionEvent) {
        JFileChooser aoChooser = new JFileChooser();
        aoChooser.setDialogTitle("Select .ao file to inspect");
        aoChooser.setFileFilter(new FileNameExtensionFilter("AO files (*.ao)", "ao"));
        aoChooser.setAcceptAllFileFilterUsed(false);
        if (aoChooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File file = aoChooser.getSelectedFile();
        if (file == null || !file.exists() || !file.isFile() || !file.getName().toLowerCase().endsWith(".ao")) {
            showError("The file '" + (file == null ? "" : file) + "' does not exist.");
            return;
        }

        SwingWorker<Void, String> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                progressBar.setValue(0);
                setUIEnabled(false);
                Inspector.inspect(file, (msg, type) -> SwingUtilities.invokeLater(() -> appendLog(msg, type)));
                return null;
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                for (String message : chunks)
                    appendLog(message);
            }

            @Override
            protected void done() {
                setUIEnabled(true);
            }

        };

        appendLog("");

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
        inspectButton.setEnabled(enabled);
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

}