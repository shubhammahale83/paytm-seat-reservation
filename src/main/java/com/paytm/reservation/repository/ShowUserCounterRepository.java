package com.paytm.reservation.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ShowUserCounterRepository {

    private final JdbcTemplate jdbcTemplate;

    public ShowUserCounterRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Map<String, Object>> findByShowIdAndUserId(UUID showId, UUID userId) {
        String sql = "SELECT * FROM show_user_counters WHERE show_id = ? AND user_id = ?";
        List<Map<String, Object>> counters = jdbcTemplate.queryForList(sql, showId, userId);
        return counters.stream().findFirst();
    }

    public int incrementCount(UUID showId, UUID userId) {
        String sql = "INSERT INTO show_user_counters (id, show_id, user_id, reserved_count) " +
                     "VALUES (?, ?, ?, 1) " +
                     "ON CONFLICT (show_id, user_id) " +
                     "DO UPDATE SET reserved_count = show_user_counters.reserved_count + 1, updated_at = CURRENT_TIMESTAMP";
        return jdbcTemplate.update(sql, UUID.randomUUID(), showId, userId);
    }

    public int decrementCount(UUID showId, UUID userId) {
        String sql = "UPDATE show_user_counters SET reserved_count = GREATEST(0, reserved_count - 1), updated_at = CURRENT_TIMESTAMP " +
                     "WHERE show_id = ? AND user_id = ?";
        return jdbcTemplate.update(sql, showId, userId);
    }
}
