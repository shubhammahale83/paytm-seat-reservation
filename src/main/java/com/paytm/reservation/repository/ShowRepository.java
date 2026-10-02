package com.paytm.reservation.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ShowRepository {

    private final JdbcTemplate jdbcTemplate;

    public ShowRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> findAll() {
        String sql = "SELECT * FROM shows ORDER BY show_time ASC";
        return jdbcTemplate.queryForList(sql);
    }

    public Optional<Map<String, Object>> findById(UUID id) {
        String sql = "SELECT * FROM shows WHERE id = ?";
        List<Map<String, Object>> shows = jdbcTemplate.queryForList(sql, id);
        return shows.stream().findFirst();
    }

    public List<Map<String, Object>> findByStatus(String status) {
        String sql = "SELECT * FROM shows WHERE status = ? ORDER BY show_time ASC";
        return jdbcTemplate.queryForList(sql, status);
    }

    public UUID save(String title, String description, String venue, Timestamp showTime, int totalSeats, int availableSeats, String status) {
        UUID id = UUID.randomUUID();
        String sql = "INSERT INTO shows (id, title, description, venue, show_time, total_seats, available_seats, status) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, id, title, description, venue, showTime, totalSeats, availableSeats, status);
        return id;
    }

    public int updateAvailableSeats(UUID showId, int availableSeats) {
        String sql = "UPDATE shows SET available_seats = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        return jdbcTemplate.update(sql, availableSeats, showId);
    }

    public int decrementAvailableSeats(UUID showId, int count) {
        String sql = "UPDATE shows SET available_seats = available_seats - ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        return jdbcTemplate.update(sql, count, showId);
    }

    public int updateStatus(UUID showId, String status) {
        String sql = "UPDATE shows SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        return jdbcTemplate.update(sql, status, showId);
    }
}
