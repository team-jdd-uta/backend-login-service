package com.teamuta.loginservice.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.teamuta.loginservice.repository.LoginRepository;
import com.teamuta.loginservice.dto.LoginResponse;

import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Service
public class LoginService {
    private final LoginRepository loginRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public LoginService(LoginRepository loginRepository) {
        this.loginRepository = loginRepository;
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

        // create outbox event payload (simple JSON)
        String eventId = UUID.randomUUID().toString();
        String payload = String.format("{\"eventId\":\"%s\",\"userId\":\"%s\",\"email\":\"%s\",\"name\":\"%s\",\"occurredAt\":%d,\"eventVersion\":1}",
            eventId, userId, username, username, nowMillis);

        loginRepository.saveOutboxEvent(eventId, "user", userId, "UserRegistered", payload, "PENDING", nowMillis);

        return new LoginResponse.User(userId, username);
    }

    public boolean exists(String username) {
        return loginRepository.existsByUsername(username);
    }

}
