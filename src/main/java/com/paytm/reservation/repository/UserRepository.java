package com.paytm.reservation.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class UserRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Map<String, Object>> findByUsername(String username) {
        String sql = "SELECT * FROM users WHERE username = ?";
        List<Map<String, Object>> users = jdbcTemplate.queryForList(sql, username);
        return users.stream().findFirst();
    }

    public Optional<Map<String, Object>> findById(UUID id) {
        String sql = "SELECT * FROM users WHERE id = ?";
        List<Map<String, Object>> users = jdbcTemplate.queryForList(sql, id);
        return users.stream().findFirst();
    }

    public UUID save(String username, String password, String email, String role) {
        UUID id = UUID.randomUUID();
        String sql = "INSERT INTO users (id, username, password, email, role) VALUES (?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, id, username, password, email, role);
        return id;
    }
}
