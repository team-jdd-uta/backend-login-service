package com.teamuta.loginservice.dto;

public record UserRegisteredEvent(
        String eventId,
        String userId,
        String email,
        String name,
        long occurredAt,
        int eventVersion
) {
}
