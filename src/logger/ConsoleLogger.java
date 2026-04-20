package logger;

import java.time.LocalDateTime;

public class ConsoleLogger implements Logger {

    /**
     * Writes the log message to the console
     * @param level the severity level
     * @param message the log message to record
     */
    @Override
    public void log(LogLevel level, String message) {
        String logMessage = LocalDateTime.now() + " [" + level + "] " + message;
        System.out.println(logMessage);
    }
}
