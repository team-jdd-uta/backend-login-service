package com.teamuta.loginservice.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.teamuta.loginservice.repository.LoginRepository;
import com.teamuta.loginservice.dto.LoginResponse;
import com.teamuta.loginservice.dto.UserRegisteredEvent;

import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class LoginService {
    private final LoginRepository loginRepository;
    private final ObjectMapper objectMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public LoginService(LoginRepository loginRepository, ObjectMapper objectMapper) {
        this.loginRepository = loginRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public LoginResponse.User register(String username, String password) {
        if (loginRepository.existsByUsername(username)) {
            return null; // already exists
        }

        String userId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        long nowMillis = System.currentTimeMillis();
        String passwordHash = passwordEncoder.encode(password);

        // save user
        loginRepository.saveUser(userId, username, passwordHash, now);

        String eventId = UUID.randomUUID().toString();
        UserRegisteredEvent event = new UserRegisteredEvent(eventId, userId, username, username, nowMillis, 1);
        String payload = serializeEvent(event);

        loginRepository.saveOutboxEvent(eventId, "user", userId, "UserRegistered", payload, "PENDING", nowMillis);

        return new LoginResponse.User(userId, username);
    }

    public boolean exists(String username) {
        return loginRepository.existsByUsername(username);
    }

    private String serializeEvent(UserRegisteredEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to serialize user registered event", exception);
        }
    }
}
