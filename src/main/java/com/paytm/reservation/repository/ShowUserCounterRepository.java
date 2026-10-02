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

    public int ensureCounterExists(UUID showId, UUID userId) {
        String sql = "INSERT INTO show_user_counters (id, show_id, user_id, reserved_count) " +
                     "VALUES (?, ?, ?, 0) " +
                     "ON CONFLICT (show_id, user_id) DO NOTHING";
        return jdbcTemplate.update(sql, UUID.randomUUID(), showId, userId);
    }

    public Optional<Map<String, Object>> findAndLockCounter(UUID showId, UUID userId) {
        String sql = "SELECT * FROM show_user_counters WHERE show_id = ? AND user_id = ? FOR UPDATE";
        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql, showId, userId);
        return list.stream().findFirst();
    }

    public int incrementCount(UUID showId, UUID userId) {
        return incrementCountBy(showId, userId, 1);
    }

    public int incrementCountBy(UUID showId, UUID userId, int count) {
        String sql = "UPDATE show_user_counters SET reserved_count = reserved_count + ?, updated_at = CURRENT_TIMESTAMP " +
                     "WHERE show_id = ? AND user_id = ?";
        return jdbcTemplate.update(sql, count, showId, userId);
    }

    public int decrementCount(UUID showId, UUID userId) {
        String sql = "UPDATE show_user_counters SET reserved_count = GREATEST(0, reserved_count - 1), updated_at = CURRENT_TIMESTAMP " +
                     "WHERE show_id = ? AND user_id = ?";
        return jdbcTemplate.update(sql, showId, userId);
    }
}
