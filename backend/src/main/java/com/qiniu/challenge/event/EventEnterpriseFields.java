package com.qiniu.challenge.event;

import jakarta.validation.constraints.Size;
import java.util.List;

public record EventEnterpriseFields(
        @Size(max = 128)
        String project,

        Long ownerUserId,

        @Size(max = 32)
        String status,

        @Size(max = 32)
        String priority,

        List<@Size(max = 64) String> tags,

        @Size(max = 64)
        String eventType,

        String customFields) {
}
