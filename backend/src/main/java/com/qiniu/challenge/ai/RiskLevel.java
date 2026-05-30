package com.qiniu.challenge.ai;

import com.fasterxml.jackson.annotation.JsonValue;

public enum RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    @JsonValue
    public String value() {
        return name().toLowerCase();
    }

    public static RiskLevel fromValue(String value) {
        if (value == null || value.isBlank()) {
            return LOW;
        }
        return RiskLevel.valueOf(value.trim().toUpperCase());
    }

    public boolean requiresConfirmation() {
        return this == HIGH || this == CRITICAL;
    }

    public RiskLevel max(RiskLevel other) {
        return ordinal() >= other.ordinal() ? this : other;
    }
}
