package com.paytm.reservation.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    public Optional<Map<String, Object>> findById(Long id) {
        String sql = "SELECT * FROM users WHERE id = ?";
        List<Map<String, Object>> users = jdbcTemplate.queryForList(sql, id);
        return users.stream().findFirst();
    }

    public int save(String username, String password, String email, String role) {
        String sql = "INSERT INTO users (username, password, email, role) VALUES (?, ?, ?, ?)";
        return jdbcTemplate.update(sql, username, password, email, role);
    }
}
