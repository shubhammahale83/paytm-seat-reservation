package com.paytm.reservation.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ShowDto {

    private UUID id;

    @JsonProperty("title")
    @JsonAlias({"name", "title"})
    private String title;

    private String description;

    private String venue;

    @JsonProperty("showTime")
    @JsonAlias({"showTime", "show_time"})
    private Timestamp showTime;

    @JsonProperty("totalSeats")
    @JsonAlias({"totalSeats", "total_seats"})
    private Integer totalSeats;

    @JsonProperty("availableSeats")
    @JsonAlias({"availableSeats", "available_seats"})
    private Integer availableSeats;

    private String status;

    private List<String> seats;

    @JsonProperty("pricePaise")
    @JsonAlias({"price_paise", "pricePaise"})
    private Long pricePaise;

    public ShowDto() {
    }

    public ShowDto(UUID id, String title, String description, String venue, Timestamp showTime, int totalSeats, int availableSeats, String status) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.venue = venue;
        this.showTime = showTime;
        this.totalSeats = totalSeats;
        this.availableSeats = availableSeats;
        this.status = status;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getVenue() { return venue != null ? venue : "Main Arena"; }
    public void setVenue(String venue) { this.venue = venue; }

    public Timestamp getShowTime() { return showTime != null ? showTime : Timestamp.from(Instant.now().plusSeconds(86400)); }
    public void setShowTime(Timestamp showTime) { this.showTime = showTime; }

    public int getTotalSeats() {
        if (seats != null && !seats.isEmpty()) {
            return seats.size();
        }
        return totalSeats != null ? totalSeats : 0;
    }
    public void setTotalSeats(Integer totalSeats) { this.totalSeats = totalSeats; }

    public int getAvailableSeats() {
        return availableSeats != null ? availableSeats : getTotalSeats();
    }
    public void setAvailableSeats(Integer availableSeats) { this.availableSeats = availableSeats; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public List<String> getSeats() { return seats; }
    public void setSeats(List<String> seats) { this.seats = seats; }

    public Long getPricePaise() { return pricePaise != null ? pricePaise : 25000L; }
    public void setPricePaise(Long pricePaise) { this.pricePaise = pricePaise; }
}


