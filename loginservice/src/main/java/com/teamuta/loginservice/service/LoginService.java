package com.teamuta.loginservice.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.teamuta.loginservice.repository.LoginRepository;
import com.teamuta.loginservice.dto.LoginResponse;
import com.teamuta.loginservice.dto.UserRegisteredEvent;

import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class LoginService {
    private final LoginRepository loginRepository;
    private final ObjectMapper objectMapper;
    private final CognitoAuthService cognitoAuthService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public LoginService(LoginRepository loginRepository, ObjectMapper objectMapper, CognitoAuthService cognitoAuthService) {
        this.loginRepository = loginRepository;
        this.objectMapper = objectMapper;
        this.cognitoAuthService = cognitoAuthService;
    }

    @Transactional
    public LoginResponse.User register(String username, String password) {
        if (loginRepository.existsByUsername(username)) {
            return null; // already exists
        }

        boolean cognitoUserCreated = false;
        try {
            cognitoAuthService.createUser(username, password);
            cognitoUserCreated = true;

            String userId = UUID.randomUUID().toString();
            LocalDateTime now = LocalDateTime.now();
            long nowMillis = System.currentTimeMillis();
            String passwordHash = passwordEncoder.encode(password);

            loginRepository.saveUser(userId, username, passwordHash, now);

            String eventId = UUID.randomUUID().toString();
            UserRegisteredEvent event = new UserRegisteredEvent(eventId, userId, username, username, nowMillis, 1);
            String payload = serializeEvent(event);

            loginRepository.saveOutboxEvent(eventId, "user", userId, "UserRegistered", payload, "PENDING", nowMillis);

            return new LoginResponse.User(userId, username);
        } catch (RuntimeException exception) {
            if (cognitoUserCreated) {
                cognitoAuthService.deleteUser(username);
            }
            throw exception;
        }
    }

    public boolean exists(String username) {
        return loginRepository.existsByUsername(username);
    }

    public LoginResponse login(String username, String password) {
        CognitoAuthService.AuthTokens authTokens = cognitoAuthService.login(username, password);
        LoginRepository.UserRecord user = loginRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Local user does not exist for authenticated Cognito account"));
        return new LoginResponse(
                true,
                new LoginResponse.User(user.id(), user.username()),
                new LoginResponse.Tokens(authTokens.accessToken(), authTokens.idToken(), authTokens.refreshToken(), authTokens.expiresIn()),
                null
        );
    }

    private String serializeEvent(UserRegisteredEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to serialize user registered event", exception);
        }
    }
}
