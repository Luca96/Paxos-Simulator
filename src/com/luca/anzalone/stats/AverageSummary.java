package com.luca.anzalone.stats;

import com.luca.anzalone.Channel;
import com.sun.istack.internal.NotNull;

/**
 * AverageSummary is responsible to compute a set of statistics related to a set of executions
 *
 * @author Luca Anzalone
 */
public class AverageSummary extends Summary {
    private int[] initialValues;
    private int executionCount;
    // average-summary related info
    private int agreements;
    private float avgBreaking;
    private int minRounds = Integer.MAX_VALUE;
    private int maxRounds = Integer.MIN_VALUE;
    private int avgRounds = 0;
    private int minMessages = Integer.MAX_VALUE;
    private int maxMessages = Integer.MIN_VALUE;


    public AverageSummary(int executions, @NotNull int...initialValues) {
        assert executions > 0;
        assert initialValues.length > 0;

        this.initialValues  = initialValues;
        this.executionCount = executions;
    }

    /** compute the summary for [executionCount] simulations */
    public AverageSummary calculate() {
        print("Running %d executions...", executionCount);

        for (int i = 0; i < executionCount; ++i) {
            final Channel channel = new Channel(initialValues)
                    .launch();

            channel.onTermination(ch -> {
                final Summary summary = ch.summary;
                summary.finishTime();

                // track values of each summary
                // messages
                totalMessages += summary.totalMessages;
                lostMessages  += summary.lostMessages;
                duplicatedMessages += summary.duplicatedMessages;
                minMessages = Integer.min(minMessages, summary.totalMessages);
                maxMessages = Integer.max(maxMessages, summary.totalMessages);
                // nodes
                brokenEvents += summary.brokenEvents;
                // execution
                avgRounds += summary.rounds;
                minRounds = Integer.min(minRounds, summary.rounds);
                maxRounds = Integer.max(maxRounds, summary.rounds);
                timeElapsed   += summary.timeElapsed;
                agreements    += summary.agreement ? 1 : 0;
            });

            print("> execution %d/%d completed", i + 1, executionCount);
        }

        // average values
        totalMessages = Math.floorDiv(totalMessages, executionCount);
        lostMessages  = Math.floorDiv(lostMessages, executionCount);
        timeElapsed   = Math.floorDiv(timeElapsed, executionCount);
        duplicatedMessages = Math.floorDiv(duplicatedMessages, executionCount);
        avgRounds   = Math.floorDiv(avgRounds, executionCount);
        avgBreaking = brokenEvents / (float) executionCount;
        totalNodes  = initialValues.length;

        return this;
    }

    // -----------------------------------------------------------------------------------------------------------------
    // -- Utilities
    // -----------------------------------------------------------------------------------------------------------------
    private String percentage(float x, float y) {
        if (x == 0)
            return "0%";

        final String s = String.format("%.2f", x / y * 100f) + "%";
        final String[] split = s.split(",");

        if ("00%".equals(split[1]))
            return split[0] + "%";

        return s.replace(",", ".");
    }

    private void print(String format, Object...args) {
        System.out.println(String.format(format, args));
    }

    @Override
    public String toString() {
        return "\nAverageSummary [\n\t" +
                "> Messages:\n\t\t" +
                "- total: [min: " + minMessages + ", avg: " + totalMessages + ", max: " + maxMessages + "]\n\t\t" +
                "- avg. lost: " + percentage(lostMessages, totalMessages) + " (" + lostMessages + ")\n\t\t" +
                "- avg. duplicated: " + percentage(duplicatedMessages, totalMessages) + " (" + duplicatedMessages + ")\n\t" +
                "> Nodes:\n\t\t" +
                "- total: " + totalNodes + "\n\t\t" +
                "- breaking per round: " + percentage(avgBreaking, brokenEvents) + " (" + Math.round(avgBreaking)+ ")\n\t" +
                "> Executions:\n\t\t" +
                "- count: " + executionCount + "\n\t\t" +
                "- rounds: [min: " + minRounds + ", avg: " + avgRounds + ", max: " + maxRounds + "]\n\t\t" +
                "- avg. time elapsed: " + timeElapsed + "ms\n\t\t" +
                "- agreements: " + percentage(agreements, executionCount) + " (" + agreements + ")\n" +
                "]";
    }
}
