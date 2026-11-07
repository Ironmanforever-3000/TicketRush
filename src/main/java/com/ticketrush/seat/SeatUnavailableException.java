package com.ticketrush.seat;

public class SeatUnavailableException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public SeatUnavailableException(String message) {
        super(message);
    }
}
