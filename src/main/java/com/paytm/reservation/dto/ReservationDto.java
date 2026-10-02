package com.paytm.reservation.dto;

import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

public class ReservationDto {

    private Long id;

    @NotNull(message = "Seat ID is required")
    private Long seatId;

    private Long userId;
    private String status;
    private OffsetDateTime reservedAt;

    public ReservationDto() {
    }

    public ReservationDto(Long id, Long seatId, Long userId, String status, OffsetDateTime reservedAt) {
        this.id = id;
        this.seatId = seatId;
        this.userId = userId;
        this.status = status;
        this.reservedAt = reservedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSeatId() {
        return seatId;
    }

    public void setSeatId(Long seatId) {
        this.seatId = seatId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getReservedAt() {
        return reservedAt;
    }

    public void setReservedAt(OffsetDateTime reservedAt) {
        this.reservedAt = reservedAt;
    }
}
