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
 * Channel is responsible for the creation, communication, and execution of the nodes.
 * The messages (sent across the channel) can be lost and/or duplicated.
 *
 * @author Luca Anzalone
 */
public class Channel {
    private final Logger log = Logger.getLogger("Channel");
    private final List<Node> nodes = new ArrayList<>();
    public  final Summary summary  = new Summary();


    public Channel(@NotNull int... values) {
        int numNodes = values.length;
        summary.totalNodes = numNodes;

        // creating nodes
        for (int rank = 0; rank < numNodes; ++rank) {
            nodes.add(new Node(this, rank, values[rank]));
        }
    }

    /** starts each node */
    public Channel launch() {
        summary.startTime();  // take the initial time

        for (Node node: nodes)
            node.start();

        return this;
    }

    /** execute the given [callback] after all nodes execution are terminated */
    public void onTermination(@NotNull Consumer<Channel> callback) {
        for (Node node: nodes) {
            try { node.join(); } catch (InterruptedException ignored) { }
        }

        callback.accept(this);
    }

    /** sends a [message] across the simulated communication channel */
    public void send(@NotNull Node from, int to, @NotNull Message message) {
        assert to < nodes.size();

        new SenderThread(from, to, message)
                .start();
    }

    /** broadcasts the given [message] */
    public void broadcast(@NotNull Node from, @NotNull Message message, boolean sendToMe) {
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

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Makes the send operation non-blocking (async)
     */
    private class SenderThread extends Thread {
        private final Node from;
        private final int to;
        private final Message message;

        SenderThread(final Node from, final int to, final Message message) {
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

            // apply network delay and errors only if receiver != sender
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

        /** simulate the network (communication) delay */
        private void sendDelay() {
            final Random generator = new Random();

            try {
                sleep(generator.nextInt(1 + CHANNEL_DELAY));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    /** simulate an error on the channel (with sudden lost of a message) */
    private boolean channelError() {
        final Random generator = new Random();
        int guess = 1 + generator.nextInt(100);

        return guess <= MESSAGE_LOST_RATE;
    }

    private void logIf(boolean flag, final String format, Object...args) {
        if (Debug.ENABLED && (flag || Debug.LOG_ALL))
            log.warning(String.format(format, args));
    }
}
