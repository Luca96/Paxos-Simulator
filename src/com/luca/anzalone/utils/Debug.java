package com.luca.anzalone.utils;

import com.sun.istack.internal.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

/**
 * costanti per il debug del codice
 */
public class Debug {
    public static boolean ENABLED = true;
    public static boolean LOG_ALL = false;
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

    // -----------------------------------------------------------------------------------------------------------------
    // -- Logging
    // -----------------------------------------------------------------------------------------------------------------
    private static final ConcurrentHashMap<String, List<String>> executionLog = new ConcurrentHashMap<>();
    private static final Timer timer = new Timer();

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

    public static void logIf(boolean flag, @NotNull String name, @NotNull String format, Object...args) {
        if (Debug.ENABLED && flag)
            log(name, format, args);
    }

    public static void logIf(boolean flag, @NotNull Round round, @NotNull String format, Object...args) {
        logIf(flag, "Round (" + round.getCount() + "):", format, args);
    }

    /** */
    public static synchronized void printLogSummary() {
        final StringBuilder sb = new StringBuilder()
                .append("--------------------------------------------------------------------\n")
                .append("------------------------  Execution Summary ------------------------\n")
                .append("--------------------------------------------------------------------\n");

        final ArrayList<String> keys = new ArrayList<>(executionLog.keySet());
        keys.sort(String::compareTo);

        for (String key: keys) {
            sb.append(key)
              .append("\n");

            executionLog.get(key).forEach(s -> {
                sb.append("\t> ")
                  .append(s)
                  .append("\n");
            });
        }
        clearConsole();
        System.out.println(sb.toString());
    }

    public static void startLogging(long rate) {
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                printLogSummary();
                try {
                    Thread.sleep(rate);
                } catch (InterruptedException ignored) { }
            }
        }, rate/2, 2^32L);
    }

    public static void stopLogging() {
        printLogSummary();
        timer.cancel();
        timer.purge();
    }

    private static void clearConsole() {
        try {
            final String os = System.getProperty("os.name");

            if (os.contains("Windows"))
                Runtime.getRuntime().exec("cls");
            else
                Runtime.getRuntime().exec("clear");

        } catch (final Exception ignored) {}
    }
}
