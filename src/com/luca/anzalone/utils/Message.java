package com.luca.anzalone.utils;

import com.sun.istack.internal.NotNull;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Messages sent across nodes
 */
public class Message {
    private Type type;
    private Round r1;
    private Round r2;
    private int value  = Integer.MIN_VALUE;
    private int sender = Integer.MIN_VALUE;

    //TODO: add @NotNull to constructors

    /** queryAlive, alive */
    public Message(@NotNull Type type) {
        this.type = type;
    }

    /** collect, accept */
    public Message(Type type, @NotNull Round r) {
        this(type);
        this.r1 = r;
    }

    /** success */
    public Message(@NotNull Type type, int value) {
        this.type  = type;
        this.value = value;
    }

    /** begin */
    public Message(Type type, Round r, int value) {
        this(type, r);
        this.value = value;
    }

    /** old-round */
    public Message(Type type, Round r1, @NotNull Round r2) {
        this(type, r1);
        this.r2 = r2;
    }

    /** last */
    public Message(Type type, Round r1, Round r2, int value) {
        this(type, r1, r2);
        this.value = value;
    }

    public Type getType() {
        return type;
    }

    public Round getR1() {
        if (r1 == null)
            return Round.empty();

        return r1;

    }

    public Round getR2() {
        if (r2 == null)
            return Round.empty();

        return r2;
    }

    public int getValue() {
        return value;
    }

    public int getSender() { return sender; }

    public void setSender(int sender) { this.sender = sender; }

    public Message copy() {
        Message m = new Message(type);
        m.r1 = (r1 == null) ? Round.empty() : r1.copy();
        m.r2 = (r2 == null) ? Round.empty() : r2.copy();
        m.value  = value;
        m.sender = sender;
        return m;
    }

    /**
     * Returns a Set of unique senders identifiers (ranks)
     */
    public static Set<Integer> uniqueSenders(@NotNull List<Message> messages){
        final Set<Integer> senders = new TreeSet<>();

        // just get the id of the senders, and add it to the set
        messages.stream().mapToInt(Message::getSender).forEach(senders::add);

        return senders;
    }

    public enum Type {
        collect,
        success,
        last,
        oldRound,
        accept,
        begin,
        queryAlive,
        alive,
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Message message = (Message) o;

        if (value != message.value) return false;
        if (type != message.type) return false;
        if (r1 != null ? !r1.equals(message.r1) : message.r1 != null) return false;
        return r2 != null ? r2.equals(message.r2) : message.r2 == null;
    }

    @Override
    public int hashCode() {
        int result = type != null ? type.hashCode() : 0;
        result = 31 * result + (r1 != null ? r1.hashCode() : 0);
        result = 31 * result + (r2 != null ? r2.hashCode() : 0);
        result = 31 * result + value;
        return result;
    }

    @Override
    public String toString() {
        switch (type) {
            case alive:
            case queryAlive:
                return String.format("Message [%s, sender: %d]",
                        type, sender);
            case last:
                return String.format("Message [last, round: %s, last_round: %s, last_value: %s, sender: %d]",
                        r1, r2, value, sender);
            case collect:
                return String.format("Message [collect, round: %s, sender: %d]",
                        r1, sender);
            case accept:
                return String.format("Message [accept, round: %s, sender: %d]",
                        r1, sender);
            case begin:
                return String.format("Message [begin, round: %s, proposed_value: %d, sender: %d]",
                        r1, value, sender);
            case success:
                return String.format("Message [success, value: %d, sender: %d]",
                        value, sender);
            case oldRound:
                return String.format("Message [oldRound, round: %s, commit: %s, sender: %d]",
                        r1, r2, sender);
        }

        return String.format("Message [type: %s, value: %d, sender: %d]",
                type, value, sender);
    }
}