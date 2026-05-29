package com.qiniu.challenge.health;

import com.qiniu.challenge.common.ApiResponse;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final String version;
    private final ZoneId timezone;

    public HealthController(
            @Value("${app.version:0.1.0}") String version,
            @Value("${app.timezone:Asia/Shanghai}") String timezone
    ) {
        this.version = version;
        this.timezone = ZoneId.of(timezone);
    }

    @GetMapping
    public ApiResponse<HealthStatusResponse> health() {
        return ApiResponse.success(new HealthStatusResponse(
                "UP",
                version,
                OffsetDateTime.now(timezone).toString()
        ));
    }
}
