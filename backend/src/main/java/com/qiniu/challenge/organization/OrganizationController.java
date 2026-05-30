package com.qiniu.challenge.organization;

import com.qiniu.challenge.auth.CurrentUserPrincipal;
import com.qiniu.challenge.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organizations")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @PostMapping
    public ApiResponse<CreateOrganizationResponse> createOrganization(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @Valid @RequestBody CreateOrganizationRequest request) {
        return ApiResponse.success(organizationService.createOrganization(principal.userId(), request));
    }

    @GetMapping
    public ApiResponse<List<OrganizationSummaryResponse>> myOrganizations(
            @AuthenticationPrincipal CurrentUserPrincipal principal) {
        return ApiResponse.success(organizationService.findMyOrganizations(principal.userId()));
    }

    @PostMapping("/{organizationId}/invite-code/refresh")
    public ApiResponse<InviteCodeResponse> refreshInviteCode(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long organizationId) {
        return ApiResponse.success(organizationService.refreshInviteCode(principal.userId(), organizationId));
    }

    @PostMapping("/join")
    public ApiResponse<JoinOrganizationResponse> joinOrganization(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @Valid @RequestBody JoinOrganizationRequest request) {
        return ApiResponse.success(organizationService.joinOrganization(principal.userId(), request));
    }

    @GetMapping("/{organizationId}/members")
    public ApiResponse<List<OrganizationMemberResponse>> members(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long organizationId) {
        return ApiResponse.success(organizationService.findMembers(principal.userId(), organizationId));
    }

    @PatchMapping("/{organizationId}/members/{userId}/role")
    public ApiResponse<Boolean> updateMemberRole(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long organizationId,
            @PathVariable long userId,
            @Valid @RequestBody UpdateMemberRoleRequest request) {
        return ApiResponse.success(organizationService.updateMemberRole(
                principal.userId(),
                organizationId,
                userId,
                request));
    }

    @DeleteMapping("/{organizationId}/members/{userId}")
    public ApiResponse<Boolean> removeMember(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long organizationId,
            @PathVariable long userId) {
        return ApiResponse.success(organizationService.removeMember(principal.userId(), organizationId, userId));
    }
}
