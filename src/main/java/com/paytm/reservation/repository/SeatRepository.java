package com.paytm.reservation.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class SeatRepository {

    private final JdbcTemplate jdbcTemplate;

    public SeatRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> findAll() {
        String sql = "SELECT * FROM seats";
        return jdbcTemplate.queryForList(sql);
    }

    public Optional<Map<String, Object>> findById(Long id) {
        String sql = "SELECT * FROM seats WHERE id = ?";
        List<Map<String, Object>> seats = jdbcTemplate.queryForList(sql, id);
        return seats.stream().findFirst();
    }

    public List<Map<String, Object>> findByStatus(String status) {
        String sql = "SELECT * FROM seats WHERE status = ?";
        return jdbcTemplate.queryForList(sql, status);
    }

    public int updateStatus(Long seatId, String status) {
        String sql = "UPDATE seats SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        return jdbcTemplate.update(sql, status, seatId);
    }
}
