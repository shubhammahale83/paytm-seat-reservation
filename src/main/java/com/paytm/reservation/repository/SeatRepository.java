package com.paytm.reservation.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class SeatRepository {

    private final JdbcTemplate jdbcTemplate;

    public SeatRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> findAll() {
        String sql = "SELECT * FROM show_seats";
        return jdbcTemplate.queryForList(sql);
    }

    public Optional<Map<String, Object>> findById(UUID id) {
        String sql = "SELECT * FROM show_seats WHERE id = ?";
        List<Map<String, Object>> seats = jdbcTemplate.queryForList(sql, id);
        return seats.stream().findFirst();
    }

    public List<Map<String, Object>> findByStatus(String status) {
        String sql = "SELECT * FROM show_seats WHERE status = ?";
        return jdbcTemplate.queryForList(sql, status);
    }

    public int updateStatus(UUID showId, String seatLabel, String status) {
        String sql = "UPDATE show_seats SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE show_id = ? AND seat_label = ?";
        return jdbcTemplate.update(sql, status, showId, seatLabel);
    }
}
