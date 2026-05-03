package com.teamuta.loginservice.dto;

public record UserRegisteredEvent(
        String eventId,
        String userId,
        String cognitoSub,
        String email,
        String name,
        long occurredAt,
        int eventVersion
) {
}
