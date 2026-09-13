package com.ticketrush.exception;

public class IdempotencyKeyReusedException extends RuntimeException {
    public IdempotencyKeyReusedException(String message) {
        super(message);
    }
}
