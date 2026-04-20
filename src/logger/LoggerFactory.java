package logger;

import java.util.concurrent.ConcurrentHashMap;

public class LoggerFactory {
    private static final ConcurrentHashMap<String, Logger> loggers = new ConcurrentHashMap<>();

    /**
     * Returns a FileLogger for the give path
     * if the specified path is already created, the cached instance is returned
     * else a new FileLogger is created with default max file size of 1MB
     * @param filePath the path of the log file
     * @return a Logger that writes to the specific file
     * @throws RuntimeException if the Logger cannot be initialized
     */
    public static Logger getFileLogger(String filePath) {
        return loggers.computeIfAbsent(filePath, path -> {
            try {
                return new FileLogger(path, 1024 * 1024);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException("Failed to create FileLogger for: " + path, e);
            }
        });
    }
    /**
     * Creates and returns a new Console Logger
     * @return a new Logger that writes to the console
     */
    public static Logger getConsoleLogger() {
        return new ConsoleLogger();
    }
}
