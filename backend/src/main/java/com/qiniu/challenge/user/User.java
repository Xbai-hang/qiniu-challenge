package com.qiniu.challenge.user;

import java.time.LocalDateTime;

public record User(
        Long id,
        String username,
        String email,
        String displayName,
        String passwordHash,
        String avatarUrl,
        UserStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime deletedAt
) {
}
