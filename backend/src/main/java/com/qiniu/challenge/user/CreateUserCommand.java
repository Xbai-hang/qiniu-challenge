package com.qiniu.challenge.user;

public record CreateUserCommand(
        String username,
        String email,
        String displayName,
        String passwordHash
) {
}
