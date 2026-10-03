package com.paytm.reservation.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class IdempotencyRecordRepository {

    private final JdbcTemplate jdbcTemplate;

    public IdempotencyRecordRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Map<String, Object>> findByKey(UUID showId, UUID userId, String idempotencyKey) {
        String sql = "SELECT * FROM idempotency_records WHERE show_id = ? AND user_id = ? AND idempotency_key = ?";
        List<Map<String, Object>> records = jdbcTemplate.queryForList(sql, showId, userId, idempotencyKey);
        return records.stream().findFirst();
    }

    public void ensureRecordExists(UUID showId, UUID userId, String idempotencyKey, String requestHash) {
        String sql = "INSERT INTO idempotency_records (id, show_id, user_id, idempotency_key, request_hash, status) " +
                     "SELECT ?, ?, ?, ?, ?, 'PROCESSING' " +
                     "WHERE NOT EXISTS (" +
                     "    SELECT 1 FROM idempotency_records WHERE show_id = ? AND user_id = ? AND idempotency_key = ?" +
                     ")";
        try {
            jdbcTemplate.update(sql, UUID.randomUUID(), showId, userId, idempotencyKey, requestHash, showId, userId, idempotencyKey);
        } catch (Exception ignored) {
            // Ignore unique key race condition
        }
    }

    public Optional<Map<String, Object>> findAndLockRecord(UUID showId, UUID userId, String idempotencyKey) {
        String sql = "SELECT * FROM idempotency_records WHERE show_id = ? AND user_id = ? AND idempotency_key = ? FOR UPDATE";
        List<Map<String, Object>> records = jdbcTemplate.queryForList(sql, showId, userId, idempotencyKey);
        return records.stream().findFirst();
    }

    public UUID save(UUID showId, UUID userId, String idempotencyKey, String requestHash, String status) {
        UUID id = UUID.randomUUID();
        String sql = "INSERT INTO idempotency_records (id, show_id, user_id, idempotency_key, request_hash, status) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, id, showId, userId, idempotencyKey, requestHash, status);
        return id;
    }

    public int updateResponse(UUID showId, UUID userId, String idempotencyKey, String responsePayload, String status) {
        String sql = "UPDATE idempotency_records SET response_payload = ?, status = ?, updated_at = CURRENT_TIMESTAMP " +
                     "WHERE show_id = ? AND user_id = ? AND idempotency_key = ?";
        return jdbcTemplate.update(sql, responsePayload, status, showId, userId, idempotencyKey);
    }
}
