package com.teamuta.loginservice.dto;

public record LoginResponse(boolean success, User user) {
    public record User(String id, String username) {
    }
}
