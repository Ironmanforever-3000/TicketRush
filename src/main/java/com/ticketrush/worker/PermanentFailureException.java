package com.ticketrush.worker;

public class PermanentFailureException extends RuntimeException {
    public PermanentFailureException(String message, Throwable cause) {
        super(message, cause);
    }
}
