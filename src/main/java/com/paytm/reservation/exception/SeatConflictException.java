package com.paytm.reservation.exception;

public class SeatConflictException extends RuntimeException {

    public SeatConflictException(String message) {
        super(message);
    }
}
