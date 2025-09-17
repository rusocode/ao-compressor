package org.aocompressor;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;

public record Logger(JTextPane textPane) {

    public Logger(JTextPane textPane) {
        this.textPane = textPane;
        initializeStyles();
    }

    public void log(String message) {
        log(message, Level.INFO);
    }

    public void success(String message) {
        log(message, Level.SUCCESS);
    }

    public void warn(String message) {
        log(message, Level.WARN);
    }

    public void error(String message) {
        log(message, Level.ERROR);
    }

    public void newLine() {
        log("");
    }

    private void log(String message, Level level) {
        StyledDocument doc = textPane.getStyledDocument();
        try {
            doc.insertString(doc.getLength(), (message == null ? "" : message) + "\n", textPane.getStyle(level.name()));
        } catch (BadLocationException ignored) {
        }
        textPane.setCaretPosition(doc.getLength());
    }

    private void initializeStyles() {
        Style base = textPane.addStyle("BASE", null);

        Style info = textPane.addStyle(Level.INFO.name(), base);
        StyleConstants.setForeground(info, new Color(0x444444));

        Style success = textPane.addStyle(Level.SUCCESS.name(), base);
        StyleConstants.setForeground(success, new Color(0x33BB4C));

        Style warn = textPane.addStyle(Level.WARN.name(), base);
        StyleConstants.setForeground(warn, new Color(0xE0950F));

        Style error = textPane.addStyle(Level.ERROR.name(), base);
        StyleConstants.setForeground(error, new Color(0xCC3333));
    }

    private enum Level {INFO, SUCCESS, WARN, ERROR}

}
