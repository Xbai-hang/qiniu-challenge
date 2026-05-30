package com.qiniu.challenge.auth;

public record AuthTokenResponse(
        String accessToken,
        AuthUserResponse user
) {
}
