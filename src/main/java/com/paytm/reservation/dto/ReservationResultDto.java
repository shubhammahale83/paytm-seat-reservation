package com.paytm.reservation.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReservationResultDto {

    @JsonProperty("showId")
    @JsonAlias({"showId", "show_id"})
    private UUID showId;

    @JsonProperty("userId")
    @JsonAlias({"userId", "user_id"})
    private UUID userId;

    private List<String> seats;

    @JsonProperty("totalAmountPaise")
    @JsonAlias({"amount_paise", "totalAmountPaise", "total_amount_paise"})
    private long totalAmountPaise;

    private String status;

    @JsonProperty("reservationIds")
    @JsonAlias({"reservationIds", "reservation_ids"})
    private List<UUID> reservationIds;

    // Dual getters for snake_case JSON field aliases
    @JsonProperty("show_id")
    public UUID getShowIdSnake() {
        return showId;
    }

    @JsonProperty("user_id")
    public UUID getUserIdSnake() {
        return userId;
    }

    @JsonProperty("amount_paise")
    public long getAmountPaiseSnake() {
        return totalAmountPaise;
    }

    @JsonProperty("reservation_id")
    public UUID getReservationIdSnake() {
        return (reservationIds != null && !reservationIds.isEmpty()) ? reservationIds.get(0) : null;
    }

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


