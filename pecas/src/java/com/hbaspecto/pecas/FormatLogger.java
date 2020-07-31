package com.hbaspecto.pecas;

import org.apache.log4j.Logger;

/**
 * Logger wrapper with logging methods that automatically apply string
 * formatting.
 * 
 * @author Graham
 */
public class FormatLogger {

    private Logger delegate;

    public FormatLogger(Logger delegate) {
        this.delegate = delegate;
    }

    public void debug(String msg, Object... args) {
        delegate.debug(String.format(msg, args));
    }

    public void debug(Throwable t, String msg, Object... args) {
        delegate.debug(String.format(msg, args), t);
    }

    public void info(String msg, Object... args) {
        delegate.info(String.format(msg, args));
    }

    public void info(Throwable t, String msg, Object... args) {
        delegate.info(String.format(msg, args), t);
    }

    public void warn(String msg, Object... args) {
        delegate.warn(String.format(msg, args));
    }

    public void warn(Throwable t, String msg, Object... args) {
        delegate.warn(String.format(msg, args), t);
    }

    public void error(String msg, Object... args) {
        delegate.error(String.format(msg, args));
    }

    public void error(Throwable t, String msg, Object... args) {
        delegate.error(String.format(msg, args), t);
    }

    public void fatal(String msg, Object... args) {
        delegate.fatal(String.format(msg, args));
    }

    public void fatal(Throwable t, String msg, Object... args) {
        delegate.fatal(String.format(msg, args), t);
    }

    /**
     * Logs a fatal error, but also throws a {@code RuntimeException} with the
     * same message
     */
    public void throwFatal(String msg, Object... args) {
        String fullMsg = String.format(msg, args);
        delegate.fatal(fullMsg);
        throw new RuntimeException(fullMsg);
    }

    /**
     * Logs a fatal error, but also throws a {@code RuntimeException} with the
     * same message and triggering exception
     */
    public void throwFatal(Throwable t, String msg, Object... args) {
        String fullMsg = String.format(msg, args);
        delegate.fatal(fullMsg, t);
        throw new RuntimeException(fullMsg, t);
    }

    /**
     * Logs a fatal error, but also throws a {@code RuntimeException} with the
     * same message. Unlike {@code throwFatal}, this method has a return type,
     * so it can be used in places that require a return value to satisfy the
     * compiler (even though this method cannot possibly return).
     */
    public <A> A throwFatalGeneric(String msg, Object... args) {
        throwFatal(msg, args);
        return null;
    }

    /**
     * Logs a fatal error, but also throws a {@code RuntimeException} with the
     * same message and triggering exception. Unlike {@code throwFatal}, this
     * method has a return type, so it can be used in places that require a
     * return value to satisfy the compiler (even though this method cannot
     * possibly return).
     */
    public <A> A throwFatalGeneric(Throwable t, String msg, Object... args) {
        throwFatal(t, msg, args);
        return null;
    }
}
