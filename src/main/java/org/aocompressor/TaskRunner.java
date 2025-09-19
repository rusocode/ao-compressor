package org.aocompressor;

import org.aocompressor.Compressor.Result;

import javax.swing.*;
import java.util.List;
import java.util.function.Supplier;

/**
 * Builder pattern for running background tasks with a progress indication.
 */

public class TaskRunner {

    /** The result of the operation. */
    private Supplier<Result> task;
    private Logger logger;
    private JProgressBar progressBar;
    private String operationType, targetPath;
    /** Pre-publish logs. */
    private Supplier<List<String>> postLogs;
    /** Callbacks for when the operation starts and finishes. */
    private Runnable onStart;
    private Runnable onFinish;

    private TaskRunner() {
    }

    public static TaskRunner run() {
        return new TaskRunner();
    }

    public TaskRunner task(Supplier<Result> task) {
        this.task = task;
        return this;
    }

    public TaskRunner logger(Logger logger) {
        this.logger = logger;
        return this;
    }

    public TaskRunner progressBar(JProgressBar progressBar) {
        this.progressBar = progressBar;
        return this;
    }

    public TaskRunner onStart(Runnable onStart) {
        this.onStart = onStart;
        return this;
    }

    public TaskRunner onFinish(Runnable onFinish) {
        this.onFinish = onFinish;
        return this;
    }

    public TaskRunner operationType(String operationType) {
        this.operationType = operationType;
        return this;
    }

    public TaskRunner targetPath(String targetPath) {
        this.targetPath = targetPath;
        return this;
    }

    public TaskRunner postLogs(Supplier<List<String>> postLogs) {
        this.postLogs = postLogs;
        return this;
    }

    public void execute() {
        new BackgroundTask().execute();
    }

    /**
     * A private class extending SwingWorker to perform background tasks with UI updates. It executes the provided task in a
     * background thread and interacts with the UI components to display progress updates and results after completion.
     * <p>
     * The BackgroundTask handles tasks that require processing a set of files while reporting progress and handling both success
     * and failure cases. The task execution begins by overriding the {@code doInBackground} method. The {@code process} method
     * publishes and handles intermediate progress log messages. The {@code done} method processes final task results or
     * exceptions.
     * <p>
     * Features of the BackgroundTask class include:
     * <ul>
     * <li>Updating a progress bar to reflect the current state of task execution.
     * <li>Logging progress and final results, with the appropriate handling for success and failure.
     * <li>Allowing external operations such as initialization and cleanup through provided hooks.
     * </ul>
     */
    private class BackgroundTask extends SwingWorker<Result, String> {

        @Override
        protected Result doInBackground() {
            SwingUtilities.invokeLater(() -> {
                onStart.run(); // Disable buttons
                progressBar.setIndeterminate(true); // Enable "marquee" animation
            });

            long start = System.nanoTime();
            Result result = task.get(); // Get the result of the operation from the task
            long time = (System.nanoTime() - start) / 1_000_000;

            if (result.success() && result.filesProcessed() > 0) {

                // Publish a message to EDT
                publish(String.format("%s %d file%s to '%s'", operationType, result.filesProcessed(), result.filesProcessed() != 1 ? "s" : "", targetPath));

                // Send additional logs
                if (postLogs != null) {
                    List<String> logs = postLogs.get();
                    if (logs != null) logs.forEach(this::publish);
                }
                publish("Time: " + time + "ms");
            }

            return result;
        }

        @Override
        protected void process(List<String> chunks) {
            chunks.forEach(logger::log); // Receives each message sent by publish() and displays them in the log
        }

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

}
