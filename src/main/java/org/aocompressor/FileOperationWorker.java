package org.aocompressor;

import org.aocompressor.Compressor.Result;

import javax.swing.*;
import java.util.List;
import java.util.function.Supplier;

/**
 * Run in the background.
 */

public class FileOperationWorker extends SwingWorker<Result, String> {

    private final Supplier<Result> result;
    private final Logger logger;
    private final JProgressBar progressBar;
    private final Runnable onStart;
    private final Runnable onFinish;
    private final String operationType; // "Compressed" or "Decompressed"
    private final String targetPath;

    public FileOperationWorker(Supplier<Result> result, Logger logger, JProgressBar progressBar, Runnable onStart, Runnable onFinish, String operationType, String targetPath) {
        this.result = result;
        this.logger = logger;
        this.progressBar = progressBar;
        this.onStart = onStart;
        this.onFinish = onFinish;
        this.operationType = operationType;
        this.targetPath = targetPath;
    }

    /* Contains the logic for the heavy task (runs in the background).
     *
     * Executes the compression task in the background, compressing files from the specified input folder into the
     * specified output file. While the task runs, the UI shows progress. */
    @Override
    protected Result doInBackground() {
        SwingUtilities.invokeLater(() -> {
            onStart.run();
            // Used to set the progress bar to indeterminate mode (marquee animation without an actual percentage)
            progressBar.setIndeterminate(true);
        });

        long start = System.nanoTime();
        Result result = this.result.get();
        long time = (System.nanoTime() - start) / 1_000_000;

        if (result.success() && result.filesProcessed() > 0) {
            String msg = String.format("%s %d file%s to '%s' in %dms",
                    operationType,
                    result.filesProcessed(),
                    result.filesProcessed() != 1 ? "s" : "",
                    targetPath,
                    time);
            publish(msg); // Send interim results (call process() method) to the EDT (Event Dispatch Thread)
        }

        return result;
    }

    // Processes intermediate results (a list of log messages) in the EDT (Event Dispatch Thread) and appends them to the log
    @Override
    protected void process(List<String> chunks) {
        chunks.forEach(logger::log);
    }

    /* Finalizes the background compression task and updates the UI accordingly.
     *
     * The Event Dispatch Thread (EDT) calls this method once the worker thread completes its background execution. Based on the
     * result of the task, the method updates the progress bar and logs and restores the state of the UI components. It also
     * handles any exceptions that occurred during the task execution. */
    @Override
    protected void done() {
        try {
            // Get the result of the operation task
            Result result = get();
            progressBar.setIndeterminate(false);

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
            logger.error("Unexpected error: " + e.getMessage());
            progressBar.setValue(0);
            onFinish.run();
        }
    }

}
