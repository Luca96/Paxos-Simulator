package com.luca.anzalone.utils;

/**
 * A set of global constants used to define the simulation environment
 *
 * @author Luca Anzalone
 */
public class Globals {
    public static int TIMEOUT = 0;                  // time (ms) for maximum message wait
    public static int CHANNEL_DELAY = 0;            // max time (ms) needed to send a message
    public static int MESSAGE_LOST_RATE = 0;        // the rate of lost messages
    public static int MESSAGE_DUPLICATION_RATE = 0; // the rate of duplicated messages
    public static int BROKEN_RATE   = 0;            // the rate of breaking a node (depend on its execution-speed)
    public static int BROKEN_TIME   = 0;            // time (ms) to repair a node
    public static int MAX_EXE_SPEED = 0;            // define the maximum execution-speed of a node
    public static int ELECTION_TIMEOUT = 0;         // time before performing a new election
}
