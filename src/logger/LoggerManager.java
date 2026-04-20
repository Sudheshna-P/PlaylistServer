package logger;

import java.util.List;

public class LoggerManager {
    private final List<Logger> loggers;

    /**
     * Constructs a LoggerManager with the provided list of loggers
     * @param loggers - loggers
     */
    public LoggerManager(List<Logger> loggers) {
        this.loggers = loggers;
    }

    private void log(LogLevel level, String msg) {
        for (Logger logger : loggers) {
            try {
                logger.log(level, msg);
            } catch (Exception e) {
                System.err.println("Logger failed " + e.getMessage());
            }
        }
    }
    /**
     * Logs a message in INFO level
     * @param message the info message
     */
    public void info(String message) {
        log(LogLevel.INFO, message);
    }

    /**
     * Logs a message at DEBUG level
     * @param message the debug message
     */
    public void debug(String message) {
        log(LogLevel.DEBUG, message);
    }

    /**
     * Logs a message at ERROR level
     * @param message the error message
     */
    public void error(String message) {
        log(LogLevel.ERROR, message);
    }
}
