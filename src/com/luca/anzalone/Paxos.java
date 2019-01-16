package com.luca.anzalone;

import com.luca.anzalone.stats.AverageSummary;
import com.luca.anzalone.utils.Debug;
import com.luca.anzalone.utils.Globals;
import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.media.jfxmedia.logging.Logger;

import java.util.Scanner;
import java.util.function.Consumer;

/**
 * Main Class
 *
 * @author Luca Anzalone
 */
public class Paxos {
    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        Logger.setLevel(Logger.DEBUG);
        title();

        // environment parameters
        Globals.CHANNEL_DELAY     = 100;
        Globals.TIMEOUT           = (Globals.CHANNEL_DELAY * 3);
        Globals.MESSAGE_LOST_RATE = 35+5;
        Globals.BROKEN_RATE       = 10;
        Globals.MESSAGE_DUPLICATION_RATE = 15;
        Globals.MAX_EXE_SPEED     = 10;
        Globals.BROKEN_TIME       = Globals.CHANNEL_DELAY * 4;
        Globals.ELECTION_TIMEOUT  = Globals.TIMEOUT + Globals.BROKEN_TIME;

        // debug profile
        Debug.CONSOLE_LOG = false;
//        Debug.LOG_ALL = true;
        Debug.MSG_RECEPTION = true;
//        Debug.MSG_SENDING   = true;
//        Debug.MSG_LOST = true;
//        Debug.MSG_DUPLICATED = true;
//        Debug.NODE_STATE  = true;
//        Debug.NODE_BROKEN = true;
//        Debug.NODE_REPAIRED = true;
//        Debug.NODE_DECISION = true;
        Debug.LOG_OLDROUND  = true;
        Debug.ELECTION_TIMEOUT = true;

        // launch with summary
        prompt("Number of simulations: ", null, input -> {
            int num = 1;
            try { num = Integer.parseInt(input); } catch (RuntimeException ignored) {}

            new AverageSummary(num, 1, 2, 0, 3)
                    .calculate()
                    .print();

            prompt("\nShow the executions log? (y/n)", "y",
                    x -> Debug.printExecutionsLog());
        });

        System.exit(0);
    }

    // -----------------------------------------------------------------------------------------------------------------

    private static void prompt(@NotNull String ask, @Nullable String answer, @NotNull Consumer<String> callback) {
        System.out.println(ask);
        final String input = scanner.nextLine().trim().toLowerCase();

        if (answer == null || answer.equals(input))
            callback.accept(input);
    }

    private static void title() {
        System.out.println("-----------------------------------------------------------------");
        System.out.println("------------------------ Paxos Simulator ------------------------");
        System.out.println("-----------------------------------------------------------------");
        System.out.println("-- version: 1.0");
        System.out.println("-- author : Luca Anzalone");
        System.out.println("-----------------------------------------------------------------\n");
    }
}
