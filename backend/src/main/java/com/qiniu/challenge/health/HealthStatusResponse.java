package com.qiniu.challenge.health;

public record HealthStatusResponse(
        String status,
        String version,
        String time
) {
}
