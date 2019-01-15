package com.luca.anzalone;

import com.luca.anzalone.utils.Debug;
import com.luca.anzalone.utils.Globals;
import com.luca.anzalone.utils.Message;
import com.luca.anzalone.utils.Round;
import com.sun.istack.internal.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.logging.Logger;

import static com.luca.anzalone.utils.Globals.*;
import static com.luca.anzalone.utils.Message.Type.*;
import static com.luca.anzalone.Node.State.*;

/**
 * The Node class simulated a distributed node.
 *
 * A node i capable of reading, sending and storing messages across the [channel].
 * In the execution of the node program, the logic round (or computation step) is represented by the advance method.
 * At any computation step, the node can be subject to a breaking.
 * After a defined amount [Globals.BROKEN_TIME] of time, the node can be repaired.
 *
 * @author Luca Anzalone
 */
public class Node extends Thread implements Runnable {
    private int rank;                 // unique identifier
    private int value;                // initial value assigned to the node
    private int exeSpeed;             // simulated execution speed
    private State stato = candidate;  // the state of the node at any time
    private boolean decision = false;
    private final Channel channel;
    private final Logger log;
    private final Queue<Message> messageQueue = new ConcurrentLinkedQueue<>();
    private final Set<Integer> nodesAlive     = new ConcurrentSkipListSet<>();  // keep track of the alive nodes
    //-----------------------------------------------------
    private Round round;  // current round
    private Round commit;
    private Round lastRound;
    private int lastValue;
    private int proposedValue;
    private long deltaTime = 0;
    //-----------------------------------------------------

    /**
     * Creates a node.
     *
     * @param channel: communication channel
     * @param rank: unique identifier (id)
     * @param v: the value that the node try to propose
     */
    Node(@NotNull final Channel channel, int rank, int v) {
        super("Node-" + rank);
        this.log = Logger.getLogger("Node [" + rank + "]");
        this.rank = rank;

        this.value = v;
        this.lastValue = v;
        this.proposedValue = v;

        this.round  = new Round(0, rank);
        this.commit = this.round.copy();
        this.lastRound = this.round.copy();

        this.channel  = channel;
        this.exeSpeed = 1 + generator.nextInt(MAX_EXE_SPEED);
    }

    @Override
    public void run() {
        deltaTime = currentTime();  // take initial execution time

        while (!decision) {
            switch (stato) {
                case voter:
                    voterPhase();
                    advance();
                    break;

                case leader:
                    leaderPhase();
                    break;

                case broken:
                    brokenPhase();
                    break;

                case candidate:
                    electionPhase();
                    break;
            }

            if (isElectionTimeoutExpired()) {
                Debug.logIf(Debug.ELECTION_TIMEOUT, "Election-Timeout", toString());
                logIf(Debug.ELECTION_TIMEOUT, "Election-Timeout", toString());

                deltaTime = currentTime();
                stato = candidate;
            }
        }

        logIf(Debug.NODE_STATE, toString());
        Debug.logIf(Debug.NODE_STATE, round, toString());
    }


    /***
     * The voter phase is divided into 2 more phases:
     *   - phase 1: reading collect messages, communicating the [lastRound] and [lastValue];
     *   - phase 2: reading begin messages, accepting the received value according to [commit]
     */
    private void voterPhase() {
        final String phaseName = "Round-voter (" + round.getCount() + "):";

        // consuming collect messages
        filterMessages(collect).forEach(msg -> {
            final Round r = msg.getR1();
            final int sender = msg.getSender();

            if (r.greaterEqual(commit)) {
                channel.send(this, sender,
                        new Message(last, r.copy(), lastRound.copy(), lastValue)
                );

                commit = r.copy();
                channel.summary.updateRound(commit);
            } else {
                channel.send(this, sender, new Message(oldRound, r.copy(), commit.copy()));
                Debug.logIf(Debug.LOG_OLDROUND, phaseName, "[OLD-ROUND in collect] %s", msg);
            }
        });

        // consuming begin messages
        filterMessages(begin).forEach(msg -> {
            final Round r = msg.getR1();
            final int v   = msg.getValue();
            final int sender = msg.getSender();

            if (r.greaterEqual(commit)) {
                channel.send(this, sender, new Message(accept, round));
                channel.summary.updateRound(r);

                lastRound = r.copy();
                lastValue = v;
            } else {
                channel.send(this, sender, new Message(oldRound, r.copy(), commit.copy()));
                Debug.logIf(Debug.LOG_OLDROUND, phaseName, "[OLD-ROUND in begin] %s", msg);
            }
        });
    }


    /**
     * The Leader phase:
     *   - part 1: collecting a majority of values
     *   - part 2: propose a value, and, then confirming its acceptation by the others
     *
     * The reception of [old-round] messages cause the current leader to lost its "leading" and became a voter.
     * The leader is, at the same time, a voter. Thus, the [collect] and [begin] messages are sent to itself.
     */
    private void leaderPhase() {
        final String phaseName = "Round-leader (" + round.getCount() + "):";
        round = nextRound();
        channel.summary.updateRound(round);

        // -- phase 1
        // -------------------------------------------------
        channel.broadcast(this, new Message(collect, round), true);
        Debug.log(phaseName, "[leader-%d] collect", rank);

        // wait a majority of last messages
        long last_timeout = currentTime() + TIMEOUT;
        final Set<Integer> lastCount = new TreeSet<>();
        boolean last_majority = false;

        while (currentTime() < last_timeout) {
            voterPhase();

            if (filterMessages(oldRound).size() > 0) {
                logIf(Debug.LOG_OLDROUND, "ricevuto old-round in collect");
                Debug.log(phaseName, "[leader-%d] 'old_round' in collect", rank);
                stato = voter;
                return;  // lascia il passo
            }

            final List<Message> lastMessages = filterMessages(last);
            lastCount.addAll(Message.uniqueSenders(lastMessages));

            // cosider the value of [v] associated to the biggest [round]
            for (Message msg: lastMessages) {
                final Round r = msg.getR1();

                if (r.greaterEqual(lastRound)) {
                    lastRound = r.copy();
                    proposedValue = msg.getValue();
                }
            }

            if (majority(lastCount.size())) {
                last_majority = true;
                break;
            }

            if (advance() == Status.changed)
                return;
        }

        if (!last_majority) {
            // no last-majority, so start another round
            logIf(Debug.LOG_TIMEOUT, "TIMEOUT EXPIRED! -- nessuna maggioranza di [last]");
            Debug.log(phaseName, "[leader-%d] last timeout expired", rank);
            return;
        }

        // -- phase 2
        // -------------------------------------------------
        channel.broadcast(this, new Message(begin, round, proposedValue), true);
        Debug.log(phaseName, "[leader-%d] begin", rank);

        // wait a majority of accept messages
        long accept_timeout = currentTime() + TIMEOUT;
        final Set<Integer> acceptCount = new TreeSet<>();

        while (currentTime() < accept_timeout) {
            voterPhase();

            if (filterMessages(oldRound).size() > 0) {
                logIf(Debug.LOG_OLDROUND, "ricevuto old-round in begin");
                Debug.log(phaseName, "[leader-%d] 'old_round' in begin", rank);
                stato = voter;
                return;  // lascia il passo
            }

            final List<Message> acceptMessages = filterMessages(accept);
            acceptCount.addAll(Message.uniqueSenders(acceptMessages));

            if (majority(acceptCount.size())) {
                // there's a decision!
                decision = true;
                value = proposedValue;
                channel.summary.decidedValue(rank, value);
                channel.broadcast(this, new Message(success, value));
                Debug.log(phaseName, "[leader-%d] 'success' => %d", rank, value);
                return;  // terminate
            }

            if (advance() == Status.changed)
                return;
        }

        logIf(Debug.LOG_TIMEOUT, "TIMEOUT EXPIRED! -- nessuna maggioranza di [accept]");
    }


    /**
     * The Election phase:
     * Every node (alive - not broken) sends a [query-alive] message in order to know the participants.
     * The leader became the node with the lowest rank (according to the known nodes by each of them).
     *
     * Is possible, due to a lost of messages, that one or more nodes became leader.
     */
    private void electionPhase() {
        final String phaseName = "Round-election (" + round.getCount() + "):";
        long timeout = currentTime() + TIMEOUT;

        nodesAlive.clear();
        nodesAlive.add(rank);
        Debug.log(phaseName, "candidate-%d queryAlive", rank);

        // try to know the other nodes
        channel.broadcast(this, new Message(queryAlive), true);
        int minRank = rank;

        while (currentTime() < timeout) {
            final List<Message> aliveMessages = filterMessages(alive);

            for (Message msg: aliveMessages) {
                int node = msg.getSender();
                if (node < minRank)
                    minRank = node;
            }

            if (advance() == Status.changed)
                return;
        }

        // elect the known node with the lowest rank
        stato = (rank == minRank) ? leader : voter;

        Debug.log(phaseName, "elezione terminata {%s}", this);
    }

    /**
     * The Broken phase:
     * According to [Globals.BROKEN_RATE] a node can incur into breaking.
     * If so, the state of the node (state, known nodes, rounds and last-values) are restore.
     * The repaired node starts again from being a candidate.
     */
    private void brokenPhase() {
        long broken_wait = currentTime() + BROKEN_TIME;

        while (currentTime() < broken_wait)
            delay();

        messageQueue.clear();
        nodesAlive.clear();
        stato = candidate;

        Debug.logIf(Debug.NODE_REPAIRED, round, "%s è stato riparato!", this);
        logIf(Debug.NODE_REPAIRED, "è stato riparato!");

        // TODO: cambiare il valore proposto con uno di default?
        // reset memoria nodo
        lastValue     = value;
        proposedValue = value;
        round  = new Round(0, rank);
        commit = round.copy();
        lastRound = round.copy();
    }

    /**
     * Reception of a message.
     * Messages are received only if the node is not broken, and they are stored into a queue.
     */
    public void receive(@NotNull Message msg) {
        // receive messages only if not broken
        if (broken.equals(stato))
            return;

        logIf(Debug.MSG_RECEPTION, "message received: %s", msg);
        Debug.logIf(Debug.MSG_RECEPTION, round, "Node-%d received %s", rank, msg);

        // update the known-node-set
        nodesAlive.add(msg.getSender());

        // enqueue the received message
        messageQueue.add(msg);

        // duplication event
        if (msg.getSender() != rank && duplication()) {
            Debug.logIf(Debug.MSG_DUPLICATED, round, "%s da [%d] a [%d] è stato duplicato!",
                    msg, msg.getSender(), rank
            );

            channel.summary.duplicatedMessages++;
            messageQueue.add(msg);
        }
    }


    /**
     * The computation step (aka logic round).
     *
     * Where:
     *   - round-independent messages are read,
     *   - execution speed is simulated,
     *   - and the success is spread (when received)
     */
    private Status advance() {
        delay();

        if (canBroke()) {
            Debug.log(round, "%s si è rotto!", this);
            channel.summary.brokenEvents++;

            if (leader.equals(stato))
                logIf(Debug.NODE_BROKEN, "LEADER è temporaneamente DOWN!");
            else
                logIf(Debug.NODE_BROKEN, "è temporaneamente DOWN!");

            stato = broken;
            return Status.changed;
        }

        // round-independent message check
        // ------------------------------------------------------------------
        final List<Message> successMessages = filterMessages(success);

        // QUERY-ALIVE
        for (Message msg: filterMessages(queryAlive)) {
            channel.send(this, msg.getSender(), new Message(alive));
        }

        // SUCCESS
        if (successMessages.size() > 0) {
            int valueDecided = successMessages.get(0).getValue();
            decision = true;
            value = valueDecided;
            channel.summary.decidedValue(rank, value);

            logIf(Debug.NODE_DECISION, "ha deciso %d", value);
            Debug.log(round, "Node-%d-%s ha deciso %d", rank, stato, value);
            Debug.log(SUCCESS_PHASE, "Node-%d-%s ha deciso %d", rank, stato, value);

            // spread (to others) the success
            channel.broadcast(this, new Message(success, value));

            return Status.changed;
        }

        logIf(Debug.NODE_STATE, this.toString());
        Debug.logIf(Debug.NODE_STATE, round, this.toString());

        return Status.alive;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Node node = (Node) o;

        return rank == node.rank;
    }

    public int getRank() {

        return rank;
    }

    public int getValue() {
        return value;
    }

    public Round getRound() {
        return round;
    }

    /** state of the node */
    enum State {
        leader,
        voter,
        broken,
        candidate,
    }

    /** status of the node */
    enum Status {
        alive,
//        dead,
        changed,
    }

    @Override
    public String toString() {
        return String.format("Node-%d [%s, round: %s, commit: %s, value: %d, nodes: %d]",
                rank, stato, round, commit, proposedValue, nodesAlive.size());
    }

    //------------------------------------------------------------------------------------------------------------------
    //-- UTILITY
    //------------------------------------------------------------------------------------------------------------------

    private void delay() {
        try { sleep(exeSpeed); } catch (InterruptedException ignored) { }
    }

    private long currentTime() {
        return java.lang.System.currentTimeMillis();
    }

    private boolean majority(int amount) {
        return (amount >= (nodesAlive.size() + 1) / 2);
    }

    /** simulate the breaking event of a node */
    private boolean canBroke() {
        return BROKEN_RATE >= 1 + generator.nextInt(1000 * Globals.MAX_EXE_SPEED);
    }

    private boolean isElectionTimeoutExpired() {
        return (currentTime() - deltaTime > Globals.ELECTION_TIMEOUT);
    }

    /** simulate the duplication event of a message */
    private boolean duplication() {
        int guess = 1 + generator.nextInt(100);
        return guess <= MESSAGE_DUPLICATION_RATE;
    }

    /** get a list of messages according to the given [type] */
    private List<Message> filterMessages(Message.Type type) {
        List<Message> selected = new ArrayList<>();

        for (Message message: messageQueue) {
            if (type.equals(message.getType())) {
                selected.add(message);
                messageQueue.remove(message);
            }
        }

        return selected;
    }

    /** get the value for the next round according to the known rounds */
    private Round nextRound() {
        if (lastRound.greaterEqual(round))
            return new Round(lastRound.getCount() + 1, this.rank);
        else
            return round.increase();
    }

    private void log(final String format, Object...args) {
        if (Debug.ENABLED)
            log.warning("[" + rank + "] " + String.format(format, args));
    }

    private void logIf(boolean flag, final String format, Object...args) {
        if (flag || Debug.LOG_ALL)
            log(format, args);
    }

    //------------------------------------------------------------------------------------------------------------------
    // -- CONSTANTS
    //------------------------------------------------------------------------------------------------------------------
    private static final Random generator = new Random();
    private static final String ELECTION_PHASE = "0. Election phase:";
    private static final String LEADER_PHASE   = "1. Leader phase:";
    private static final String VOTER_PHASE    = "2. Voter phase:";
    private static final String BROKEN_PHASE   = "3. Broken Phase:";
    private static final String SUCCESS_PHASE  = "4. Success phase:";
}
