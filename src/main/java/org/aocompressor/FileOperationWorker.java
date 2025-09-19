package org.aocompressor;

import org.aocompressor.Compressor.Result;

import javax.swing.*;
import java.util.List;
import java.util.function.Supplier;

/**
 * Run in the background.
 */

public class FileOperationWorker extends SwingWorker<Result, String> {

    /** The result of the operation. */
    private final Supplier<Result> result;
    private final Logger logger;
    private final JProgressBar progressBar;
    /** Callbacks for when the operation starts and finishes. */
    private final Runnable onStart;
    private final Runnable onFinish;
    /** Type operation (compress or decompress). */
    private final String operationType;
    private final String targetPath;
    /** Pre-publish logs. */
    private final Supplier<List<String>> prePublishLogs;

    public FileOperationWorker(Supplier<Result> result, Logger logger, JProgressBar progressBar, Runnable onStart, Runnable onFinish, String operationType, String targetPath, Supplier<List<String>> prePublishLogs) {
        this.result = result;
        this.logger = logger;
        this.progressBar = progressBar;
        this.onStart = onStart;
        this.onFinish = onFinish;
        this.operationType = operationType;
        this.targetPath = targetPath;
        this.prePublishLogs = prePublishLogs;
    }

    /* Contains the logic for the heavy task (runs in the background).
     *
     * Executes the compression/decompression tasks in the background. While the task runs, the UI shows progress. */
    @Override
    protected Result doInBackground() {
        // Prepare the UI
        SwingUtilities.invokeLater(() -> {
            onStart.run(); // Disable buttons
            progressBar.setIndeterminate(true); // Enable "marquee" animation
        });

        long start = System.nanoTime();
        Result result = this.result.get(); // Execute compress() or decompress()
        long time = (System.nanoTime() - start) / 1_000_000;


        if (result.success() && result.filesProcessed() > 0) {
            // Send a message to EDT
            publish(String.format("%s %d file%s to '%s'", operationType, result.filesProcessed(), result.filesProcessed() != 1 ? "s" : "", targetPath)); // Send interim results (call process() method) to the EDT (Event Dispatch Thread)
            if (prePublishLogs != null) {
                List<String> pre = prePublishLogs.get();
                // Send additional logs (e.g., compression statistics)
                if (pre != null) pre.forEach(this::publish);
            }
            publish("Time: " + time + "ms");
        }

        return result;
    }

    // Updates the UI by processing the intermediate results (a list of log messages) in the EDT (event dispatch thread) and adding them to the log
    @Override
    protected void process(List<String> chunks) {
        chunks.forEach(logger::log); // Receives each message sent by publish() and displays them in the log
    }

    /* Finalizes the background compression task and updates the UI accordingly.
     *
     * The Event Dispatch Thread (EDT) calls this method once the worker thread completes its background execution. Based on the
     * result of the task, the method updates the progress bar and logs and restores the state of the UI components. It also
     * handles any exceptions that occurred during the task execution. */
    @Override
    protected void done() {
        try {
            Result result = get(); // Get the result of the doInBackground()
            progressBar.setIndeterminate(false); // Disable "marquee" animation

            // If compression successful
            if (result.success()) {
                if (result.filesProcessed() > 0) {
                    progressBar.setValue(100);
                    logger.success(result.message());
                } else { // If no files found
                    progressBar.setValue(0);
                    logger.warn(result.message());
                }
            } else { // If compression fails
                progressBar.setValue(0);
                logger.error(result.message());
            }

            logger.newLine();
            onFinish.run();

        } catch (Exception e) {
            logger.error("Unexpected error.\n" + e.getMessage());
            progressBar.setValue(0);
            progressBar.setIndeterminate(false);
            logger.newLine();
            onFinish.run(); // Enable buttons
        }
    }

}
