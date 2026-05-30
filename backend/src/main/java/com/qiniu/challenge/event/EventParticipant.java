package com.qiniu.challenge.event;

public record EventParticipant(
        long userId,
        String displayName,
        String role,
        String responseStatus) {
}
