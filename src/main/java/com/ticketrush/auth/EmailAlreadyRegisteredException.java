package com.ticketrush.auth;

public class EmailAlreadyRegisteredException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public EmailAlreadyRegisteredException() {
        super("Email is already registered");
    }
}
