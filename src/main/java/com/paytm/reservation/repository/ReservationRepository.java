package com.paytm.reservation.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ReservationRepository {

    private final JdbcTemplate jdbcTemplate;

    public ReservationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> findByUserId(UUID userId) {
        String sql = "SELECT * FROM reservations WHERE user_id = ? ORDER BY created_at DESC";
        return jdbcTemplate.queryForList(sql, userId);
    }

    public List<Map<String, Object>> findByShowId(UUID showId) {
        String sql = "SELECT * FROM reservations WHERE show_id = ? ORDER BY created_at DESC";
        return jdbcTemplate.queryForList(sql, showId);
    }

    public Optional<Map<String, Object>> findById(UUID id) {
        String sql = "SELECT * FROM reservations WHERE id = ?";
        List<Map<String, Object>> reservations = jdbcTemplate.queryForList(sql, id);
        return reservations.stream().findFirst();
    }

    public UUID createReservation(UUID showId, UUID userId, String seatLabel, long pricePaise, String status, Timestamp expiresAt) {
        UUID id = UUID.randomUUID();
        String sql = "INSERT INTO reservations (id, show_id, user_id, seat_label, price_paise, status, expires_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, id, showId, userId, seatLabel, pricePaise, status, expiresAt);
        return id;
    }

    public int updateStatus(UUID reservationId, String status) {
        String sql = "UPDATE reservations SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        return jdbcTemplate.update(sql, status, reservationId);
    }
}
