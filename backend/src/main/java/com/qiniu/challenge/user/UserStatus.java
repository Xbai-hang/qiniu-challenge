package com.qiniu.challenge.user;

public enum UserStatus {
    ACTIVE("active"),
    DISABLED("disabled");

    private final String value;

    UserStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
