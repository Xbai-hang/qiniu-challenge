package com.qiniu.challenge.auth;

public record RegisterResponse(
        AuthUserResponse user,
        String accessToken,
        Long defaultSpaceId
) {
}
