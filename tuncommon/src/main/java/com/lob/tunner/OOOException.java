package com.lob.tunner;

public class OOOException extends RuntimeException {
    private final short _expect;
    private final short _sequence;
    public OOOException(short expect, short seq) {
        super("Out Of Order");

        _expect = expect;
        _sequence = seq;
    }

    public String toString() {
        return String.format("Out of order! (expecting=%d, received=%d)", _expect, _sequence);
    }
}
