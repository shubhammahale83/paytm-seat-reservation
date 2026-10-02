package com.paytm.reservation.dto;

import java.util.List;
import java.util.UUID;

public class ReservationResultDto {

    private UUID showId;
    private UUID userId;
    private List<String> seats;
    private long totalAmountPaise;
    private String status;
    private List<UUID> reservationIds;

    public ReservationResultDto() {
    }

    public ReservationResultDto(UUID showId, UUID userId, List<String> seats, long totalAmountPaise, String status, List<UUID> reservationIds) {
        this.showId = showId;
        this.userId = userId;
        this.seats = seats;
        this.totalAmountPaise = totalAmountPaise;
        this.status = status;
        this.reservationIds = reservationIds;
    }

    public UUID getShowId() {
        return showId;
    }

    public void setShowId(UUID showId) {
        this.showId = showId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public List<String> getSeats() {
        return seats;
    }

    public void setSeats(List<String> seats) {
        this.seats = seats;
    }

    public long getTotalAmountPaise() {
        return totalAmountPaise;
    }

    public void setTotalAmountPaise(long totalAmountPaise) {
        this.totalAmountPaise = totalAmountPaise;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<UUID> getReservationIds() {
        return reservationIds;
    }

    public void setReservationIds(List<UUID> reservationIds) {
        this.reservationIds = reservationIds;
    }
}
