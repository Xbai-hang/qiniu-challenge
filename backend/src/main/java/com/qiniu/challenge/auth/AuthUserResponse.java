package com.qiniu.challenge.auth;

public record AuthUserResponse(
        Long id,
        String username,
        String email,
        String displayName
) {
}
