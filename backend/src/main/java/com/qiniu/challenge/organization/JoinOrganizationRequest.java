package com.qiniu.challenge.organization;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JoinOrganizationRequest(
        @NotBlank(message = "邀请码不能为空")
        @Size(max = 64, message = "邀请码不能超过 64 个字符")
        String inviteCode
) {
}
