package com.qiniu.challenge.organization;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateOrganizationRequest(
        @NotBlank(message = "组织名称不能为空")
        @Size(max = 128, message = "组织名称不能超过 128 个字符")
        String name
) {
}
