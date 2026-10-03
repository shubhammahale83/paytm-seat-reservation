package com.paytm.reservation.dto;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Detailed representation of a show including all seat statuses and aggregate seat counts.
 * Note: This system relies on explicit cancellation rather than time-based holds,
 * therefore 'held' is always 0.
 */
public class ShowDetailDto {

    private UUID id;
    private String title;
    private String description;
    private String venue;
    private Timestamp showTime;
    private String status;
    private int total;
    private int available;
    /**
     * Always 0 because explicit cancellation is used instead of time-based holds.
     */
    private int held;
    private int confirmed;
    private List<Map<String, Object>> seats;

    public ShowDetailDto() {
    }

    public ShowDetailDto(UUID id, String title, String description, String venue, Timestamp showTime, String status, int total, int available, int held, int confirmed, List<Map<String, Object>> seats) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.venue = venue;
        this.showTime = showTime;
        this.status = status;
        this.total = total;
        this.available = available;
        this.held = held;
        this.confirmed = confirmed;
        this.seats = seats;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getVenue() {
        return venue;
    }

    public void setVenue(String venue) {
        this.venue = venue;
    }

    public Timestamp getShowTime() {
        return showTime;
    }

    public void setShowTime(Timestamp showTime) {
        this.showTime = showTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getAvailable() {
        return available;
    }

    public void setAvailable(int available) {
        this.available = available;
    }

    public int getHeld() {
        return held;
    }

    public void setHeld(int held) {
        this.held = held;
    }

    public int getConfirmed() {
        return confirmed;
    }

    public void setConfirmed(int confirmed) {
        this.confirmed = confirmed;
    }

    public List<Map<String, Object>> getSeats() {
        return seats;
    }

    public void setSeats(List<Map<String, Object>> seats) {
        this.seats = seats;
    }
}
