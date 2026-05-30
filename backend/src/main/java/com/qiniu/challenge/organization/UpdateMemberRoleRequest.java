package com.qiniu.challenge.organization;

import jakarta.validation.constraints.NotBlank;

public record UpdateMemberRoleRequest(
        @NotBlank(message = "成员角色不能为空")
        String role
) {
}
