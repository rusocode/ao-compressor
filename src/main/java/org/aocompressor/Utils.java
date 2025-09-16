package org.aocompressor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Random;
import java.util.stream.Stream;

/**
 * Utility class for general functions.
 */

public class Utils {

    /**
     * Generate random string.
     */
    public static String generateRandomString(int length, boolean upperOnly) {
        String chars = upperOnly ? "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789" : "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++)
            sb.append(chars.charAt(random.nextInt(chars.length())));
        return sb.toString();
    }

    public static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    public static long folderSize(String folderPathString) {
        var folderPath = Paths.get(folderPathString);
        if (!Files.exists(folderPath) || !Files.isDirectory(folderPath)) {
            throw new IllegalArgumentException("Path does not exist or is not a directory: " + folderPathString);
        }
        try (Stream<Path> stream = Files.walk(folderPath)) {
            return stream
                    .filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try {
                            return Files.size(p);
                        } catch (Exception e) {
                            // Si no se puede leer el tamaño de un archivo, lo contamos como 0
                            return 0L;
                        }
                    })
                    .sum();
        } catch (Exception e) {
            throw new RuntimeException("Error computing folder size for: " + folderPathString, e);
        }
    }

    /**
     * Calculates SHA-256 hash of a byte array.
     */
    public static String sha256Hex(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(md.digest(data));
    }

    public static JLabel createLink(String text, String url) {
        String html = String.format("<html><a href=\"%s\">%s</a></html>", url, escapeHtml(text));
        JLabel label = new JLabel(html);
        label.setFont(new Font("Consolas", Font.PLAIN, 11));
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        label.setToolTipText(url);
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                try {
                    if (Desktop.isDesktopSupported()) {
                        Desktop desktop = Desktop.getDesktop();
                        if (desktop.isSupported(Desktop.Action.BROWSE)) desktop.browse(new URI(url));
                        else JOptionPane.showMessageDialog(null, "The action BROWSE is not supported on this platform.");
                    } else JOptionPane.showMessageDialog(null, "The platform does not support opening links.");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(null, "The link could not be opened.");
                }
            }
        });
        return label;
    }

    /**
     * Escapes special characters in a string for use in HTML content.
     * <p>
     * This method replaces characters that have special meanings in HTML, such as '&', '<', '>', '"', and '\'', with their
     * corresponding HTML entities. It ensures that the resulting string is safe to display in a web environment without
     * introducing HTML injection vulnerabilities.
     *
     * @param text input string to escape or empty string
     * @return the escaped HTML-safe string
     */
    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

}
