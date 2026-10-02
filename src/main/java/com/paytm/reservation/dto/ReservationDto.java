package com.paytm.reservation.dto;

import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.UUID;

public class ReservationDto {

    private UUID id;

    @NotNull(message = "Seat ID is required")
    private UUID seatId;

    private UUID userId;
    private String status;
    private OffsetDateTime reservedAt;

    public ReservationDto() {
    }

    public ReservationDto(UUID id, UUID seatId, UUID userId, String status, OffsetDateTime reservedAt) {
        this.id = id;
        this.seatId = seatId;
        this.userId = userId;
        this.status = status;
        this.reservedAt = reservedAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getSeatId() {
        return seatId;
    }

    public void setSeatId(UUID seatId) {
        this.seatId = seatId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
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
