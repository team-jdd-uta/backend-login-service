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
    public LoginResponse.User register(String email, String nickname, String password) {
        String trimmedEmail = email == null ? "" : email.trim();
        String trimmedNickname = nickname == null ? "" : nickname.trim();

        if (trimmedEmail.isBlank() || trimmedNickname.isBlank() || password == null || password.isBlank()) {
            throw new IllegalArgumentException("email, nickname, password are required");
        }

        if (loginRepository.existsByEmail(trimmedEmail)) {
            return null; // already exists
        }
        if (loginRepository.existsByNickname(trimmedNickname)) {
            return null;
        }

        boolean cognitoUserCreated = false;
        try {
            String cognitoSub = cognitoAuthService.createUser(trimmedEmail, password);
            cognitoUserCreated = true;

            String userId = UUID.randomUUID().toString();
            LocalDateTime now = LocalDateTime.now();
            long nowMillis = System.currentTimeMillis();
            String passwordHash = passwordEncoder.encode(password);

            loginRepository.saveUser(userId, trimmedEmail, cognitoSub, trimmedNickname, passwordHash, now);

            String eventId = UUID.randomUUID().toString();
            UserRegisteredEvent event = new UserRegisteredEvent(eventId, userId, cognitoSub, trimmedEmail, trimmedNickname, nowMillis, 2);
            String payload = serializeEvent(event);

            loginRepository.saveOutboxEvent(eventId, "user", userId, "UserRegistered", payload, "PENDING", nowMillis);

            return new LoginResponse.User(userId, trimmedNickname, trimmedEmail);
        } catch (RuntimeException exception) {
            if (cognitoUserCreated) {
                cognitoAuthService.deleteUser(trimmedEmail);
            }
            throw exception;
        }
    }

    public boolean exists(String username) {
        return loginRepository.existsByUsername(username);
    }

    public java.util.Optional<LoginRepository.UserRecord> findByCognitoSub(String cognitoSub) {
        if (cognitoSub == null || cognitoSub.isBlank()) {
            return java.util.Optional.empty();
        }
        return loginRepository.findByCognitoSub(cognitoSub.trim());
    }

    public LoginResponse login(String username, String password) {
        CognitoAuthService.AuthTokens authTokens = cognitoAuthService.login(username, password);
        LoginRepository.UserRecord user = loginRepository.findByEmail(username)
                .orElseThrow(() -> new LoginFailureException("계정 정보가 아직 준비되지 않았습니다. 잠시 후 다시 시도해주세요."));
        return new LoginResponse(
                true,
                new LoginResponse.User(user.id(), user.name(), user.email()),
                new LoginResponse.Tokens(authTokens.accessToken(), authTokens.idToken(), authTokens.refreshToken(), authTokens.expiresIn()),
                null
        );
    }

    public LoginResponse refresh(String refreshToken) {
        CognitoAuthService.AuthTokens authTokens = cognitoAuthService.refresh(refreshToken);
        return new LoginResponse(
                true,
                null,
                new LoginResponse.Tokens(authTokens.accessToken(), authTokens.idToken(), authTokens.refreshToken(), authTokens.expiresIn()),
                null
        );
    }

    public static class LoginFailureException extends RuntimeException {
        public LoginFailureException(String message) {
            super(message);
        }

        public LoginFailureException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private String serializeEvent(UserRegisteredEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to serialize user registered event", exception);
        }
    }
}
