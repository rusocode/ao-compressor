package org.aocompressor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Utility class for general functions.
 */

public final class Utils {

    private Utils() {
    }

    public static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    public static long getDirectorySize(Path directory) {
        if (!Files.isDirectory(directory)) {
            showError("Invalid '" + directory + "'.");
            return 0L;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            return paths
                    .filter(Files::isRegularFile)
                    .mapToLong(Utils::getFileSize)
                    .sum();
        } catch (Exception e) {
            showError("Error calculating folder size.\n" + e.getMessage());
            return 0L;
        }
    }

    public static String getFileName(Path file) {
        String fileName = file.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    public static JLabel createLink(String text, String url) {
        JLabel label = new JLabel(String.format("<html><a href=\"%s\">%s</a></html>", url, text));
        label.setFont(new Font("Consolas", Font.PLAIN, 11));
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        label.setToolTipText(url);
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                try {
                    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
                        Desktop.getDesktop().browse(new URI(url));
                    else showError("Opening links is not supported on this platform.");
                } catch (Exception ex) {
                    showError("Could not open link.\n" + ex.getMessage());
                }
            }
        });
        return label;
    }

    public static void showError(String message) {
        JOptionPane.showMessageDialog(null, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private static long getFileSize(Path path) {
        try {
            return Files.size(path);
        } catch (Exception e) {
            return 0L;
        }
    }

}
