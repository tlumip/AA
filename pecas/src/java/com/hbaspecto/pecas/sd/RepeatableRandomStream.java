package com.hbaspecto.pecas.sd;

/**
 * Interface for repeatable, infinite streams of "random" numbers.
 *
 */
public interface RepeatableRandomStream {
    /**
     * Returns the next random number in the stream.
     */
    public double next();
}
