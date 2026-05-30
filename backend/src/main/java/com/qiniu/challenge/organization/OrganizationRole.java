package com.qiniu.challenge.organization;

import com.qiniu.challenge.common.ApiException;
import com.qiniu.challenge.common.ErrorCode;
import java.util.Locale;

public enum OrganizationRole {
    OWNER("owner", 0),
    ADMIN("admin", 1),
    MEMBER("member", 2);

    private final String value;
    private final int sortOrder;

    OrganizationRole(String value, int sortOrder) {
        this.value = value;
        this.sortOrder = sortOrder;
    }

    public String value() {
        return value;
    }

    public int sortOrder() {
        return sortOrder;
    }

    public boolean canRefreshInviteCode() {
        return this == OWNER || this == ADMIN;
    }

    public boolean canManageMembers() {
        return this == OWNER || this == ADMIN;
    }

    public static OrganizationRole fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "成员角色不能为空");
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "owner" -> OWNER;
            case "admin" -> ADMIN;
            case "member" -> MEMBER;
            default -> throw new ApiException(ErrorCode.BAD_REQUEST, "不支持的成员角色");
        };
    }
}
