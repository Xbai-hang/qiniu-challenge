package com.qiniu.challenge.event;

public record ParticipantCommand(
        long userId,
        String role,
        String responseStatus) {
}
