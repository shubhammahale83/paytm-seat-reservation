package com.paytm.reservation.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ShowSeatRepository {

    private final JdbcTemplate jdbcTemplate;

    public ShowSeatRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> findByShowId(UUID showId) {
        String sql = "SELECT * FROM show_seats WHERE show_id = ? ORDER BY seat_label ASC";
        return jdbcTemplate.queryForList(sql, showId);
    }

    public Optional<Map<String, Object>> findByShowIdAndSeatLabel(UUID showId, String seatLabel) {
        String sql = "SELECT * FROM show_seats WHERE show_id = ? AND seat_label = ?";
        List<Map<String, Object>> seats = jdbcTemplate.queryForList(sql, showId, seatLabel);
        return seats.stream().findFirst();
    }

    public List<Map<String, Object>> findByShowIdAndStatus(UUID showId, String status) {
        String sql = "SELECT * FROM show_seats WHERE show_id = ? AND status = ? ORDER BY seat_label ASC";
        return jdbcTemplate.queryForList(sql, showId, status);
    }

    public UUID save(UUID showId, String seatLabel, String seatTier, long pricePaise, String status) {
        UUID id = UUID.randomUUID();
        String sql = "INSERT INTO show_seats (id, show_id, seat_label, seat_tier, price_paise, status) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, id, showId, seatLabel, seatTier, pricePaise, status);
        return id;
    }

    public int updateStatus(UUID showId, String seatLabel, String status) {
        String sql = "UPDATE show_seats SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE show_id = ? AND seat_label = ?";
        return jdbcTemplate.update(sql, status, showId, seatLabel);
    }
}
