package com.paytm.reservation.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class ReservationRepository {

    private final JdbcTemplate jdbcTemplate;

    public ReservationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> findByUserId(Long userId) {
        String sql = "SELECT * FROM reservations WHERE user_id = ?";
        return jdbcTemplate.queryForList(sql, userId);
    }

    public Optional<Map<String, Object>> findById(Long id) {
        String sql = "SELECT * FROM reservations WHERE id = ?";
        List<Map<String, Object>> reservations = jdbcTemplate.queryForList(sql, id);
        return reservations.stream().findFirst();
    }

    public int createReservation(Long userId, Long seatId, String status) {
        String sql = "INSERT INTO reservations (user_id, seat_id, status) VALUES (?, ?, ?)";
        return jdbcTemplate.update(sql, userId, seatId, status);
    }

    public int updateStatus(Long reservationId, String status) {
        String sql = "UPDATE reservations SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        return jdbcTemplate.update(sql, status, reservationId);
    }
}
