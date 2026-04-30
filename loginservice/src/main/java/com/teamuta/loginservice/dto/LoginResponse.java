package com.teamuta.loginservice.dto;

public record LoginResponse(boolean success, User user, Tokens tokens, String message) {
    public LoginResponse(boolean success, User user) {
        this(success, user, null, null);
    }

    public LoginResponse(boolean success, User user, String message) {
        this(success, user, null, message);
    }

    public record User(String id, String username) {
    }

    public record Tokens(String accessToken, String idToken, String refreshToken, Integer expiresIn) {
    }
}
