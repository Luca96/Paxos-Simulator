package com.luca.anzalone;

import com.luca.anzalone.utils.Debug;
import com.luca.anzalone.utils.Globals;
import com.luca.anzalone.utils.Message;
import com.luca.anzalone.utils.Round;
import com.sun.istack.internal.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

import static com.luca.anzalone.utils.Globals.*;
import static com.luca.anzalone.utils.Message.Type.*;
import static com.luca.anzalone.Node.State.*;

/**
 * Classe che manipola i nodi all'interno del sistema distribuito
 */
public class Node extends Thread implements Runnable {
    private int rank;                 // identificatore univoco del nodo
    private int value;                // valore iniziale del nodo
    private int exeSpeed;             // velocità di esecuzione di un nodo (clock)
    private State stato = candidate;  // inizialmente i nodi sono dei candidati da eleggere a leader
    private boolean decision = false;
    private TreeSet<Integer> nodesAlive = new TreeSet<>();  // tiene traccia dei noti attivi (non guasti)
    private final Channel channel;
    private final Logger log;
    private final ConcurrentLinkedQueue<Message> messageQueue = new ConcurrentLinkedQueue<>();

    //-----------------------------------------------------
    private Round round;  // round corrente
    private Round commit;
    private Round lastRound;
    private int lastValue;
    private int proposedValue;
    //-----------------------------------------------------

    /**
     * Crea un processore (nodo)
     * @param channel: canale di comunicazione
     * @param rank: identificatore univoco del processore
     * @param v: valore associato al processore (cioè quello da proporre)
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
        }

        logIf(Debug.NODE_STATE, toString());
        Debug.logIf(Debug.NODE_STATE, round, toString());
    }

    /**
     * Fase da voter:
     * Il voter, ad ogni passo di computazione, controlla l'eventuale ricezione di messaggi success, collect e begin.
     *
     * Se riceve success (e tutti i messaggi sono concordanti - stesso valore deciso) deciderà e si fermerà.
     *
     * Se non riceve messaggi collect e begin per un certo periodo di tempo, controllerà lo stato di vita del leader.
     * Se il leader non risponde con una conferma (alive) entro il timeout, il nodo comunicherà in broadcast la
     * necessità di iniziare una nuova elezione.
     */
    private void voterPhase() {
        final String phaseName = "Round-voter (" + round.getCount() + "):";

        // se ricevo collect...
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
                Debug.logIf(Debug.LOG_OLDROUND, phaseName, "[OLD-ROUND in collect] %s",
                        new Message(oldRound, r.copy(), commit.copy()));
            }
        });

        // se ricevo begin...
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
                Debug.logIf(Debug.LOG_OLDROUND, phaseName, "[OLD-ROUND in begin] %s",
                        new Message(oldRound, r.copy(), commit.copy()));
            }
        });
    }


    /**
     * Fase da leader:
     * Il leader invia i messaggi collect per conoscere i valori degli altri nodi (che risponderanno con last).
     * Tiene traccia del valore associato all'[r] più grande.
     *
     * Ricevuta una maggioranza, decidera proprio per quel valore - solo se una maggioranza lo accetterà.
     * Raggiunto il successo (la decisione), questa sarà comunicata in broadcast a tutti i nodi partecipanti.
     *
     * Il leader, dopo il successo, attenderà gli ack per due timeout. Dopo il quale terminerà.
     */
    private void leaderPhase() {
        final String phaseName = "Round-leader (" + round.getCount() + "):";
        round = nextRound();
        channel.summary.updateRound(round);

        // -- fase 1
        // -------------------------------------------------
        channel.broadcast(this, new Message(collect, round), true);
        Debug.log(phaseName, "[leader-%d] collect", rank);

        // aspetta messaggi last
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

            // considera il valore [v] associato al [round] più grande
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
            logIf(Debug.LOG_TIMEOUT, "TIMEOUT EXPIRED! -- nessuna maggioranza di [last]");
            Debug.log(phaseName, "[leader-%d] last timeout expired", rank);
            return;   // nessuna maggiornanza di last, inizia un nuovo round
        }

        // -- fase 2
        // -------------------------------------------------
        // spedisci begin
        channel.broadcast(this, new Message(begin, round, proposedValue), true);
        Debug.log(phaseName, "[leader-%d] begin", rank);

        // aspetta una maggioranza di accept
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
                // c'è una decisione!
                decision = true;
                value = proposedValue;
                channel.summary.decidedValue(rank, value);
                channel.broadcast(this, new Message(success, value));
                Debug.log(phaseName, "[leader-%d] 'success' => %d", rank, value);
                return;
            }

            if (advance() == Status.changed)
                return;
        }

        logIf(Debug.LOG_TIMEOUT, "TIMEOUT EXPIRED! -- nessuna maggioranza di [accept]");
    }


    /**
     * Fase di elezione:
     * Un nodo sceglie un processore da eleggere, selezionando un rank da (0, numNodes - 1).
     * Il valore scelto è comunicato a tutti (broadcast).
     *
     * Ogni nodo si appresta a ricevere una maggioranza di messaggi [election], tenendo traccia, di volta in volta, del
     * massimo valore di [rank] ricevuto.
     *
     * Il leader sarà quel nodo che avrà ricevuto una maggiornanza di messaggi [election],
     * tale che rank(nodo) = max(rank, election).
     */
    private void electionPhase() {
        final String phaseName = "Round-election (" + round.getCount() + "):";
        long timeout = currentTime() + TIMEOUT;

        nodesAlive.clear();
        nodesAlive.add(rank);
        Debug.log(phaseName, "candidate-%d queryAlive", rank);

        // tenta di conoscere i nodi non guasti
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

        // a seconda dei nodi alive, considera come leader il nodo con rank minore
        stato = (rank == minRank) ? leader : voter;

        Debug.log(phaseName, "elezione terminata {%s}", this);
    }

    /**
     * Fase di rottura del nodo:
     * Dopo un periodo di tempo (fissato dai parametri [Globals])
     * il nodo viene ripristinato alla situazione iniziale [stato = candidate].
     *
     * Prima di ricominciare (dalla fase di elezione), il nodo tenta di conoscere il leader. Se non riesce a conoscerlo
     * inizierà dalla fase di elezione come candidato.
     */
    private void brokenPhase() {
        long broken_wait = currentTime() + BROKEN_TIME;

        while (currentTime() < broken_wait)
            delay();

        messageQueue.clear();
        nodesAlive.clear();
        stato = candidate;

        Debug.logIf(Debug.NODE_REPAIRED, round, "%s è stato riparato!", this);

        // reset memoria nodo
        // TODO: cambiare il valore proposto con uno di default?
        lastValue     = value;
        proposedValue = value;
        round  = new Round(0, rank);
        commit = round.copy();
        lastRound = round.copy();

        logIf(Debug.NODE_REPAIRED, "è stato riparato!");
    }

    /**
     * Ricezione di un messaggio dal canale.
     * Se il nodo è temporaneamente [broken] il messaggio non sarà salvato nel buffer [messageQueque],
     * altrimenti (nodo funzionante) il messaggio è accodato nel buffer - per essere esaminato quando necessario.
     */
    public void receive(@NotNull Message msg) {
        // non riceve il messaggio se rotto
        if (broken.equals(stato))
            return;

        logIf(Debug.MSG_RECEPTION, "message received: %s", msg);
        Debug.logIf(Debug.MSG_RECEPTION, round, "Node-%d received %s", rank, msg);

        // tieni traccia dei nodi partecipanti al consenso
        nodesAlive.add(msg.getSender());

        // accoda
        messageQueue.add(msg);

        // duplicazione
        if (msg.getSender() != rank && duplication()) {
            Debug.logIf(Debug.MSG_DUPLICATED, round, "%s da [%d] a [%d] è stato duplicato!",
                    msg, msg.getSender(), rank
            );

            channel.summary.duplicatedMessages++;
            messageQueue.add(msg);
        }
    }

    /**
     * Passo di computazione, con controllo dei messaggi "speciali".
     * Nell'avanzamento della computazione, il nodo può rompersi oppure cambiare stato a causa dei messaggi ricevuti.
     *
     * Controlla l'eventuale ricezione di messaggi success - in tal caso deciderà, comunicando agli altri il successo.
     * Se leader, risponderà ad eventuali messaggi di [queryAlive] con il proprio rank.
     * Ignora i messaggi [oldRound].
     * E, se in elezione, controlla chi è divenuto leader.
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

        // controllo messaggi
        // ------------------------------------------------------------------
        final List<Message> successMessages = filterMessages(success);

        // QUERY-ALVE
        for (Message msg: filterMessages(queryAlive)) {
            channel.send(this, msg.getSender(), new Message(alive));
        }

        // SUCCESS
        if (successMessages.size() > 0) {
            int valueDecided = successMessages.get(0).getValue();
            decision = true;
            value = valueDecided;
            channel.summary.decidedValue(rank ,value);

            logIf(Debug.NODE_DECISION, "ha deciso %d", value);
            Debug.log(round, "Node-%d-%s ha deciso %d", rank, stato, value);
            Debug.log(SUCCESS_PHASE, "Node-%d-%s ha deciso %d", rank, stato, value);

            // diffondi, agli altri, l'avvenuto successo
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

    /** tipologia del nodo */
    enum State {
        leader,
        voter,
        broken,
        candidate,
    }

    /** stato del nodo */
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
        try {
            sleep(exeSpeed);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private long currentTime() {
        return java.lang.System.currentTimeMillis();
    }

    private boolean majority(int amount) {
        return (amount >= (nodesAlive.size() + 1) / 2);
    }

    /** simula l'evento di rottura di un nodo */
    private boolean canBroke() {
        return BROKEN_RATE >= 1 + generator.nextInt(1000 * Globals.MAX_EXE_SPEED);
    }

    /** simula l'evento di duplicazione dell'invio di un messaggio */
    private boolean duplication() {
        int guess = 1 + generator.nextInt(100);
        return guess <= MESSAGE_DUPLICATION_RATE;
    }

    /** seleziona i messagi per tipo */
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

    /** fornisce il valore di round adatto (maggiore del valore dei round conosciuti) per il prossimo round */
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
    // -- COSTANTS
    //------------------------------------------------------------------------------------------------------------------
    private static final Random generator = new Random();
    private static final String ELECTION_PHASE = "0. Fase di Elezione:";
    private static final String LEADER_PHASE   = "1. Fase da Leader:";
    private static final String VOTER_PHASE    = "2. Fase da Voter:";
    private static final String BROKEN_PHASE   = "3. Fase di Rottura:";
    private static final String SUCCESS_PHASE  = "3. Decisione:";
}
