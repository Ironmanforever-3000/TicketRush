package com.ticketrush.exception;

public class IdempotencyKeyReusedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public IdempotencyKeyReusedException(String message) {
        super(message);
    }
}
