package com.hbaspecto.pecas.sd.estimation;

/**
 * Thrown to indicate that a problem has occurred during an optimization.
 * @author Graham
 *
 */
@SuppressWarnings("serial")
public class OptimizationException extends Exception {

    public OptimizationException() {
    }

    public OptimizationException(String message) {
        super(message);
    }

    public OptimizationException(Throwable cause) {
        super(cause);
    }

    public OptimizationException(String message, Throwable cause) {
        super(message, cause);
    }

}
