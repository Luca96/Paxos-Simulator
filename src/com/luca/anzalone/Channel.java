package com.luca.anzalone;

import com.luca.anzalone.stats.Summary;
import com.luca.anzalone.utils.Debug;
import com.luca.anzalone.utils.Message;
import com.sun.istack.internal.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static com.luca.anzalone.utils.Globals.*;

/**
 * Corrisponde al sistema distribuito:
 * si occupa di creare ed avviare i processi,
 * di trasferire i messaggi (che possono essere perduti) - mettendo in comunicazione i nodi
 */
public class Channel extends Thread {
    private final Logger log = Logger.getLogger("Channel");
    private final List<Node> nodes = new ArrayList<>();
    public final Summary summary = new Summary();

    public Channel(@NotNull int... values) {
        int numNodes = values.length;
        summary.totalNodes = numNodes;

        // creazione dei nodi
        for (int rank = 0; rank < numNodes; ++rank) {
            nodes.add(new Node(this, rank, values[rank]));
        }
    }

    /** avvia i nodi */
    public Channel launch() {
        summary.startTime();

        for (Node node: nodes)
            node.start();

        return this;
    }

    /** attende la terminazione dei nodi, dopo la quale esegue una [callback] */
    public void onTermination(Consumer<Channel> callback) {
        for (Node node: nodes) {
            try {
                node.join();

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        callback.accept(this);
    }

    /**
     * verifica che ogni nodo abbia deciso lo stesso valore
     * @return [true]: se l'accordo è verificato, [false] altrimenti.
     */
    public boolean verificaAccordo() {
        int value = nodes.get(0).getValue();

        for (int i = 1; i < nodes.size(); ++i) {
            if (value != nodes.get(i).getValue())
                return false;
        }

        return true;
    }

    /** invia un messaggio lungo il canale */
    public void send(@NotNull Node from, int to, @NotNull Message message) {
        assert to < nodes.size();

        new Thread(new SenderRunnable(from, to, message))
                .start();
    }

    /** Invia il messaggio a tutti i nodi: opzionalmente esclude se stesso. */
    public void broadcast(@NotNull Node from, @NotNull Message message, boolean sendToMe) {
//        log.warning("nodo: " + from.getRank() + " broadcast [" + message.getType() + "]");

        for (Node node: nodes) {
            if (!sendToMe && from.equals(node))
                continue;

            send(from, node.getRank(), message.copy());
        }
    }

    /** shorthand */
    public void broadcast(@NotNull Node from, @NotNull Message message) {
        broadcast(from, message, false);
    }

    /**
     * Runnable per rendere l'operazione di send non-bloccante
     */
    private class SenderRunnable implements Runnable {
        private final Node from;
        private final int to;
        private final Message message;

        SenderRunnable(final Node from, final int to, final Message message) {
            this.from = from;
            this.to = to;
            this.message = message;
            this.message.setSender(from.getRank());

            logIf(Debug.MSG_SENDING, "Invio di %s da [%d] a [%d]", message, from.getRank(), to);
            summary.totalMessages++;
        }

        @Override
        public void run() {
            final Node receiver = nodes.get(to);

            // applica il delay (e perdite) della rete solo se receiver != sender
            if (from.getRank() != receiver.getRank()) {
                if (channelError()) {
                    summary.lostMessages++;
                    logIf(Debug.MSG_LOST, "%s da [%d] a [%d] è stato perso", message, from.getRank(), to);
                    Debug.log(from.getRound(), "%s da [%d] a [%d] è stato perso", message, from.getRank(), to);
                    return;
                }

                sendDelay();
            }

            receiver.receive(message);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    private boolean channelError() {
        final Random generator = new Random();
        int guess = 1 + generator.nextInt(100);

        return guess <= MESSAGE_LOST_RATE;
    }

    /** simula il ritardo della rete */
    private void sendDelay() {
        final Random generator = new Random();

        try {
            sleep(generator.nextInt(1 + CHANNEL_DELAY));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void logIf(boolean flag, final String format, Object...args) {
        if (Debug.ENABLED && (flag || Debug.LOG_ALL))
            log.warning(String.format(format, args));
    }
}
