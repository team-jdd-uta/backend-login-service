package com.teamuta.loginservice.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public class LoginRepository {

	private final JdbcTemplate jdbc;

	public LoginRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public boolean existsByEmail(String email) {
		Integer count = jdbc.queryForObject(
				"SELECT COUNT(*) FROM users WHERE email = ?",
				Integer.class,
				email
		);
		return count != null && count > 0;
	}

	public boolean existsByNickname(String nickname) {
		Integer count = jdbc.queryForObject(
				"SELECT COUNT(*) FROM users WHERE name = ?",
				Integer.class,
				nickname
		);
		return count != null && count > 0;
	}

	public void saveUser(String userId, String email, String cognitoSub, String name, String passwordHash, LocalDateTime createdAt) {
		jdbc.update(
				"INSERT INTO users (user_id, email, cognito_sub, name, password_hash, created_at) VALUES (?, ?, ?, ?, ?, ?)",
				userId, email, cognitoSub, name, passwordHash, Timestamp.valueOf(createdAt)
		);
	}

	public void saveOutboxEvent(String eventId, String aggregateType, String aggregateId, String eventType, String payload, String status, long createdAtMillis) {
		jdbc.update(
				"INSERT INTO outbox_event (event_id, aggregate_type, aggregate_id, event_type, payload, status, created_at, published_at) VALUES (?, ?, ?, ?, ?, ?, ?, NULL)",
				eventId, aggregateType, aggregateId, eventType, payload, status, createdAtMillis
		);
	}

	public Optional<UserRecord> findByEmail(String email) {
		return jdbc.query(
				"SELECT user_id, email, name, cognito_sub FROM users WHERE email = ?",
				(rs, rowNum) -> new UserRecord(rs.getString("user_id"), rs.getString("email"), rs.getString("name"), rs.getString("cognito_sub")),
				email
		).stream().findFirst();
	}

	public Optional<UserRecord> findByCognitoSub(String cognitoSub) {
		return jdbc.query(
				"SELECT user_id, email, name, cognito_sub FROM users WHERE cognito_sub = ?",
				(rs, rowNum) -> new UserRecord(rs.getString("user_id"), rs.getString("email"), rs.getString("name"), rs.getString("cognito_sub")),
				cognitoSub
		).stream().findFirst();
	}

	public boolean existsByUsername(String username) {
		return existsByEmail(username);
	}

	public Optional<UserRecord> findByUsername(String username) {
		return findByEmail(username);
	}

	public record UserRecord(String id, String email, String name, String cognitoSub) {
	}
}
