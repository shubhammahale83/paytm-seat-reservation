package com.paytm.reservation.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public class ReserveSeatRequest {

    @NotEmpty(message = "Seats list cannot be empty")
    private List<String> seats;

    public ReserveSeatRequest() {
    }

    public ReserveSeatRequest(List<String> seats) {
        this.seats = seats;
    }

    public List<String> getSeats() {
        return seats;
    }

    public void setSeats(List<String> seats) {
        this.seats = seats;
    }
}
