package com.luca.anzalone;

import com.luca.anzalone.stats.AverageSummary;
import com.luca.anzalone.utils.Debug;
import com.luca.anzalone.utils.Globals;
import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.media.jfxmedia.logging.Logger;

import java.util.Scanner;
import java.util.function.Consumer;

public class Paxos {
    private static final Scanner scanner = new Scanner(System.in);

    /**
     * Main contiene il necessario per l'avvio dell'algoritmo: settare i parametri [Globals] in accordo con la
     * situazione che si vuole simulare (frequenza dei guasti ai nodi, e frequenza di perdita dei messaggi).
     *
     * L'oggetto [Channel] si occupa anche di creare ed avviare i nodi. Channel riceve in input un array di interi
     * associati ad ogni nodo creato.
     *
     * [verificaAccordo] blocca il main-thread in attesa che i nodi terminino per verificare che, effettivamente, ci
     * sia un accordo tra il valore deciso tra i nodi.
     */
    public static void main(String[] args) {
        Logger.setLevel(Logger.DEBUG);
        title();

        // init parameters
        Globals.CHANNEL_DELAY     = 100;
        Globals.TIMEOUT           = (Globals.CHANNEL_DELAY * 3);
        Globals.MESSAGE_LOST_RATE = 35;
//        Globals.BROKEN_RATE       = 25;
        Globals.MESSAGE_DUPLICATION_RATE = 10;
        Globals.MAX_EXE_SPEED     = 10;
        Globals.BROKEN_TIME       = Globals.CHANNEL_DELAY * 4;

        // debug profile
        Debug.ENABLED = false;
//        Debug.MSG_RECEPTION = true;
//        Debug.MSG_SENDING   = true;
        Debug.MSG_LOST = true;
//        Debug.MSG_DUPLICATED = true;
        Debug.NODE_STATE  = true;
        Debug.NODE_BROKEN = true;
//        Debug.NODE_REPAIRED = true;
        Debug.NODE_DECISION = true;
        Debug.LOG_OLDROUND  = true;

        // avvio
        prompt("Numero di esecuzioni: ", null, input -> {
            int num = 1;
            try { num = Integer.parseInt(input); } catch (RuntimeException ignored) {}

            new AverageSummary(num, 1, 2, 0, 3)
                    .calculate()
                    .print();

            prompt("\nMostrare log delle esecuzioni? (si/no)", "si",
                    x -> Debug.printLogSummary());
        });

        System.exit(0);
    }

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
        System.out.println("-- author: Luca Anzalone");
        System.out.println("-----------------------------------------------------------------\n");
    }
}
