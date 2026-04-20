package logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

class FileLogger implements Logger {

    private final String filePath;
    private final long maxFileSize;

    private BufferedWriter bw;
    private final BlockingQueue<String> logQueue = new LinkedBlockingQueue<>(10000);

    private volatile boolean running = true;
    private Thread workerThread; // keep reference to worker

    /**
     * Logs the messages in File
     * @param filePath - path of file
     * @param maxFileSize - maximum size of file
     * @throws RuntimeException is the file cannot be opened or created
     */
    public FileLogger(String filePath, long maxFileSize) {
        this.filePath = filePath;
        this.maxFileSize = maxFileSize;

        try {
            this.bw = new BufferedWriter(new FileWriter(filePath, true));
            System.out.println(">>> FileLogger path: " + new File(filePath).getAbsolutePath()); // ADD HERE
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize FileLogger", e);
        }

        startWorker();

        // Shutdown hook — ONLY signal and wait
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                running = false;
                //workerThread.interrupt();
                workerThread.join(5000);
            } catch (Exception e) {
                System.err.println("Shutdown failed: " + e.getMessage());
            }

        }));
    }

    /**
     * Enqueues the log messages to be written to the file
     * @param level the severity level of the message
     * @param message the log message
     */
    @Override
    public void log(LogLevel level, String message) {
        String logMessage = LocalDateTime.now() + " [" + level + "] " + message;

        try {
            if (!logQueue.offer(logMessage)) {
                System.err.println("Log queue full, dropping message: " + logMessage);
            }
        } catch (Exception e) {
            System.err.println("Failed to enqueue log: " + e.getMessage());
        }
    }

    /**
     * Starts the background worker thread that writes the entries into the file
     * the thread is marked Daemon
     */


    private void startWorker() {
        workerThread = new Thread(() -> {
            try {
                while (running || !logQueue.isEmpty()) {
                    String logMessage = logQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (logMessage == null) continue;
                    rotateFileIfNeeded();
                    bw.write(logMessage);
                    bw.newLine();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                System.err.println("Worker thread failed: " + e.getMessage());
            } finally {
                drainQueue();

                try {
                    bw.flush();
                    bw.close();
                } catch (IOException e) {
                    System.err.println("Final flush failed: " + e.getMessage());
                }
            }
        });
        //workerThread.setDaemon(true);
        workerThread.start();
    }

    private void drainQueue() {
        try {
            String msg;
            while ((msg = logQueue.poll()) != null) {
                bw.write(msg);
                bw.newLine();
            }
        } catch (IOException e) {
            System.err.println("Drain failed: " + e.getMessage());
        }
    }

    /**
     * Checks whether the current log file exceeded the maxFileSize and,
     * if so, it renames the file with timestamp and opens a new log file
     * @throws IOException if the file cannot be opened or closed
     */
    private void rotateFileIfNeeded() throws IOException {

        File file = new File(filePath);

        if (!file.exists() || file.length() < maxFileSize) return;

        bw.close();

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss.SSS"));
        String rotatedName = filePath.replace(".log", "") + "-" + timestamp + ".log";

        File rotatedFile = new File(rotatedName);

        if (!file.renameTo(rotatedFile)) {
            throw new IOException("Failed to rotate log file from " + filePath + " to " + rotatedName);
        }

        deleteOldLogs();

        bw = new BufferedWriter(new FileWriter(filePath, true));
    }

    /**
     * Deletes the oldest rotated log backup files when the number of backups
     *      * exceeds the maximum allowed limit.
     *      * the maximum number of backup files is 5
     *      * @see FileLogger
     */
    private void deleteOldLogs() {
        File parentDir = new File(filePath).getParentFile();

        if (parentDir == null) parentDir = new File(".");

        String baseName = new File(filePath).getName().replace(".log", "");

        File[] files = parentDir.listFiles((dir, name) ->
                name.startsWith(baseName + "-") && name.endsWith(".log"));

        int maxBackupFiles = 5;
        if (files == null || files.length <= maxBackupFiles) return;

        Arrays.sort(files, Comparator.comparingLong(File::lastModified));

        for (int i = 0; i < files.length - maxBackupFiles; i++) {
            if (!files[i].delete()) {
                System.err.println("Failed to delete old log file: " + files[i].getAbsolutePath());
            }
        }
    }
}
