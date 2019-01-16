package com.luca.anzalone.utils;

import com.sun.istack.internal.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A set of constants and methods for handy logging and debugging
 *
 * @author Luca Anzalone
 */
public class Debug {
    // flags for logging
    public static boolean CONSOLE_LOG = true;  // enable or disable console log
    public static boolean LOG_ALL = false;     // enable all logging flags
    public static boolean MSG_RECEPTION;
    public static boolean MSG_SENDING;
    public static boolean MSG_LOST;
    public static boolean MSG_DUPLICATED;
    public static boolean NODE_STATE;
    public static boolean NODE_BROKEN;
    public static boolean NODE_REPAIRED;
    public static boolean NODE_DECISION;
    public static boolean LOG_OLDROUND;
    public static boolean LOG_TIMEOUT;
    public static boolean ELECTION_TIMEOUT;

    // -----------------------------------------------------------------------------------------------------------------
    // -- Logging
    // -----------------------------------------------------------------------------------------------------------------
    private static final ConcurrentHashMap<String, List<String>> executionLog = new ConcurrentHashMap<>();

    /** adds a new entry to [executionLog] */
    public static synchronized void log(@NotNull String name, @NotNull String format, Object...args) {
        List<String> logs = executionLog.containsKey(name) ? executionLog.get(name) : new ArrayList<>();
        logs.add(String.format(format, args));

        executionLog.put(name, logs);
    }

    /** log by round */
    public static void log(@NotNull Round round, @NotNull String format, Object...args) {
        log("Round (" + round.getCount() + "):", format, args);
    }

    /** log conditionally: according to a flag */
    public static void logIf(boolean flag, @NotNull String name, @NotNull String format, Object...args) {
        if (flag || LOG_ALL)
            log(name, format, args);
    }

    public static void logIf(boolean flag, @NotNull Round round, @NotNull String format, Object...args) {
        logIf(flag, "Round (" + round.getCount() + "):", format, args);
    }

    /**
     * shows the logs collected from all executions
     */
    public static synchronized void printExecutionsLog() {
        final StringBuilder sb = new StringBuilder()
                .append("---------------------------------------------------------------------\n")
                .append("------------------------  Executions Log ----------------------------\n")
                .append("---------------------------------------------------------------------\n");

        final ArrayList<String> keys = new ArrayList<>(executionLog.keySet());
        keys.sort(String::compareTo);

        for (String key: keys) {
            sb.append(key)
              .append("\n");

            executionLog.get(key).forEach(s -> sb.append("\t> ")
              .append(s)
              .append("\n"));
        }
        System.out.println(sb.toString());
    }
}
