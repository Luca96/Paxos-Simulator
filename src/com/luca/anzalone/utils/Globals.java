package com.luca.anzalone.utils;

/**
 * Classe per variabili globali da inizializzare
 */
public class Globals {
    public static int TIMEOUT = 0;                  // tempo (ms) limite di attesa per messaggi
    public static int CHANNEL_DELAY = 0;            // tempo massimo (ms) per inviare un messaggio
    public static int MESSAGE_LOST_RATE = 0;        // frequenza di perdita dei messaggi inviati lungo il canale (su 100)
    public static int MESSAGE_DUPLICATION_RATE = 0; // frequenza di messaggi duplicati (su 100)
    public static int BROKEN_RATE   = 0;            // probabilità in millesimi che un nodo si rompa
    public static int BROKEN_TIME   = 0;            // tempo (ms) dopo il quale il nodo è ripristinato
    public static int MAX_EXE_SPEED = 0;            // utilizzare per simulare la velocità di eseuzione di un nodo
}
