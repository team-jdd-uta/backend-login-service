package com.teamuta.loginservice.dto;

public record InternalUserResponse(
        String userId,
        String username,
        String email
) {
}
