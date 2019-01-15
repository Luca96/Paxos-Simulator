package com.luca.anzalone.stats;

import com.luca.anzalone.utils.Round;
import com.sun.istack.internal.NotNull;

import java.util.Map;
import java.util.TreeMap;

/**
 * Summary compute the statistics for a single execution
 */
public class Summary {
    // messages
    public int totalMessages;
    public int lostMessages;
    public int duplicatedMessages;
    // nodes
    public int totalNodes;
    public int brokenEvents;
    // execution
    public int rounds;
    public long timeElapsed;
    public boolean agreement = false;
    private Map<Integer, Integer> decisions = new TreeMap<>();

    // -----------------------------------------------------------------------------------------------------------------
    // -- METHODS
    // -----------------------------------------------------------------------------------------------------------------
    public void startTime() {
        timeElapsed = System.currentTimeMillis();  // temp
    }

    public void finishTime() {
        timeElapsed = System.currentTimeMillis() - timeElapsed;
    }

    public void updateRound(@NotNull Round round) {
        if (round.getCount() > rounds)
            rounds = round.getCount();
    }

    /** keeps track of the decided values and if they are all the same (agreement) */
    public void decidedValue(int rank, int value) {
        decisions.put(rank, value);
        agreement = decisions.values().stream().allMatch(v -> v == value);
    }

    public void print() {
        System.out.println(this);
    }

    @Override
    public String toString() {
        return "Summary [\n\t" +
                "> Messages:\n\t\t" +
                "- total: " + totalMessages + "\n\t\t" +
                "- lost: " + lostMessages + "\n\t\t" +
                "- duplicated: " + duplicatedMessages + "\n\t" +
                "> Nodes:\n\t\t" +
                "- total: " + totalNodes + "\n\t\t" +
                "- broken events: " + brokenEvents + "\n\t" +
                "> Execution:\n\t\t" +
                "- avg. rounds: " + rounds + "\n\t\t" +
                "- time elapsed: " + timeElapsed + "ms\n\t\t" +
                "- agreement: " + agreement + "\n\t\t" +
                "- decisions: " + decisions.values() + "\n\t\t" +
                "]";
    }
}
