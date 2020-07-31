package com.hbaspecto.pecas;

public class TazNumber extends TaggedIntBox {
    private TazNumber(int value) {
        super(value);
    }
    
    public static TazNumber of(int value) {
        return new TazNumber(value);
    }
}
