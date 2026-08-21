package com.brainserve.onboarding.organization.api.controller;

import com.brainserve.onboarding.common.api.ApiResponse;
import com.brainserve.onboarding.common.observability.RequestContext;
import com.brainserve.onboarding.common.security.TenantPrincipal;
import com.brainserve.onboarding.organization.api.request.AssignRolesRequest;
import com.brainserve.onboarding.organization.api.request.CreateRoleRequest;
import com.brainserve.onboarding.organization.api.request.InviteOrganizationUserRequest;
import com.brainserve.onboarding.organization.api.request.UpdateMembershipStatusRequest;
import com.brainserve.onboarding.organization.api.request.UpdateOrganizationRequest;
import com.brainserve.onboarding.organization.api.request.UpdateRoleRequest;
import com.brainserve.onboarding.organization.api.response.InvitationResponse;
import com.brainserve.onboarding.organization.api.response.OrganizationResponse;
import com.brainserve.onboarding.organization.api.response.OrganizationUserResponse;
import com.brainserve.onboarding.organization.api.response.PermissionResponse;
import com.brainserve.onboarding.organization.api.response.RoleResponse;
import com.brainserve.onboarding.organization.application.service.OrganizationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
public class OrganizationController {
    private final OrganizationService service;
    public OrganizationController(OrganizationService service) { this.service = service; }

    @GetMapping("/organizations/{organizationId}")
    @PreAuthorize("hasAuthority('ORG_READ') or hasAuthority('ORG_UPDATE')")
    ApiResponse<OrganizationResponse> organization(@AuthenticationPrincipal TenantPrincipal principal,
                                                    @PathVariable UUID organizationId) {
        return ApiResponse.success(service.getOrganization(principal, organizationId), RequestContext.requestId());
    }

    @PatchMapping("/organizations/{organizationId}")
    @PreAuthorize("hasAuthority('ORG_UPDATE')")
    ApiResponse<OrganizationResponse> updateOrganization(@AuthenticationPrincipal TenantPrincipal principal,
                                                          @PathVariable UUID organizationId,
                                                          @Valid @RequestBody UpdateOrganizationRequest body,
                                                          HttpServletRequest request) {
        return ApiResponse.success(service.updateOrganization(principal, organizationId, body, request), RequestContext.requestId());
    }

    @GetMapping("/organization-users")
    @PreAuthorize("hasAuthority('USER_READ') or hasAuthority('USER_MANAGE')")
    ApiResponse<List<OrganizationUserResponse>> users(@AuthenticationPrincipal TenantPrincipal principal,
                                                       @RequestParam(defaultValue = "0") @Min(0) int page,
                                                       @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        var result = service.listUsers(principal, page, size);
        Map<String, Object> meta = Map.of(
                "page", result.page(), "size", result.size(), "totalElements", result.totalElements(), "totalPages", result.totalPages());
        return ApiResponse.success(result.items(), meta, RequestContext.requestId());
    }

    @PostMapping("/organization-users/invitations")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    ApiResponse<InvitationResponse> invite(@AuthenticationPrincipal TenantPrincipal principal,
                                            @Valid @RequestBody InviteOrganizationUserRequest body,
                                            HttpServletRequest request) {
        return ApiResponse.success(service.inviteUser(principal, body, request), RequestContext.requestId());
    }

    @PutMapping("/organization-users/{membershipId}/roles")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    ApiResponse<OrganizationUserResponse> assignRoles(@AuthenticationPrincipal TenantPrincipal principal,
                                                       @PathVariable UUID membershipId,
                                                       @Valid @RequestBody AssignRolesRequest body,
                                                       HttpServletRequest request) {
        return ApiResponse.success(service.assignRoles(principal, membershipId, body, request), RequestContext.requestId());
    }

    @PatchMapping("/organization-users/{membershipId}/status")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    ApiResponse<OrganizationUserResponse> updateStatus(@AuthenticationPrincipal TenantPrincipal principal,
                                                        @PathVariable UUID membershipId,
                                                        @Valid @RequestBody UpdateMembershipStatusRequest body,
                                                        HttpServletRequest request) {
        return ApiResponse.success(service.updateMembershipStatus(principal, membershipId, body, request), RequestContext.requestId());
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('ROLE_READ') or hasAuthority('ROLE_MANAGE') or hasAuthority('USER_MANAGE')")
    ApiResponse<List<PermissionResponse>> permissions() {
        return ApiResponse.success(service.listPermissions(), RequestContext.requestId());
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('ROLE_READ') or hasAuthority('ROLE_MANAGE') or hasAuthority('USER_MANAGE')")
    ApiResponse<List<RoleResponse>> roles(@AuthenticationPrincipal TenantPrincipal principal) {
        return ApiResponse.success(service.listRoles(principal), RequestContext.requestId());
    }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    ApiResponse<RoleResponse> createRole(@AuthenticationPrincipal TenantPrincipal principal,
                                         @Valid @RequestBody CreateRoleRequest body,
                                         HttpServletRequest request) {
        return ApiResponse.success(service.createRole(principal, body, request), RequestContext.requestId());
    }

    @PatchMapping("/roles/{roleId}")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    ApiResponse<RoleResponse> updateRole(@AuthenticationPrincipal TenantPrincipal principal,
                                         @PathVariable UUID roleId,
                                         @Valid @RequestBody UpdateRoleRequest body,
                                         HttpServletRequest request) {
        return ApiResponse.success(service.updateRole(principal, roleId, body, request), RequestContext.requestId());
    }
}
