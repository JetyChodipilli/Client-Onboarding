package com.brainserve.clientonboarding.organization.api;

import com.brainserve.clientonboarding.common.security.CurrentPrincipal;
import com.brainserve.clientonboarding.common.security.TenantPrincipal;
import com.brainserve.clientonboarding.common.api.ApiSuccess;
import com.brainserve.clientonboarding.common.observability.RequestIds;
import com.brainserve.clientonboarding.common.observability.RequestMetadata;
import com.brainserve.clientonboarding.organization.application.OrganizationAdminService;
import com.brainserve.clientonboarding.organization.domain.model.Organization;
import com.brainserve.clientonboarding.organization.domain.model.OrganizationMember;
import com.brainserve.clientonboarding.organization.domain.model.Role;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class OrganizationController {
    private final OrganizationAdminService service;

    public OrganizationController(OrganizationAdminService service) {
        this.service = service;
    }

    @GetMapping("/organizations/{organizationId}")
    ApiSuccess<Organization> getOrganization(@PathVariable UUID organizationId, Authentication authentication) {
        return ApiSuccess.of(service.getOrganization(principal(authentication), organizationId),
                RequestIds.currentRequestId());
    }

    @PatchMapping("/organizations/{organizationId}")
    ApiSuccess<Organization> updateOrganization(@PathVariable UUID organizationId,
                                                 @Valid @RequestBody OrganizationRequest request,
                                                 Authentication authentication,
                                                 HttpServletRequest servletRequest) {
        return ApiSuccess.of(service.updateOrganization(principal(authentication), organizationId,
                request.name(), request.version(), metadata(servletRequest)), RequestIds.currentRequestId());
    }

    @GetMapping("/permissions")
    ApiSuccess<List<String>> listPermissions(Authentication authentication) {
        principal(authentication);
        return ApiSuccess.of(service.listPermissions(), RequestIds.currentRequestId());
    }

    @GetMapping("/roles")
    ApiSuccess<List<Role>> listRoles(Authentication authentication) {
        return ApiSuccess.of(service.listRoles(principal(authentication)), RequestIds.currentRequestId());
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    ApiSuccess<Role> createRole(@Valid @RequestBody RoleRequest request, Authentication authentication,
                                HttpServletRequest servletRequest) {
        var command = new OrganizationAdminService.RoleCommand(request.name(), request.description(),
                request.permissions(), 0);
        return ApiSuccess.of(service.createRole(principal(authentication), command, metadata(servletRequest)),
                RequestIds.currentRequestId());
    }

    @PatchMapping("/roles/{roleId}")
    ApiSuccess<Role> updateRole(@PathVariable UUID roleId, @Valid @RequestBody RoleRequest request,
                                Authentication authentication, HttpServletRequest servletRequest) {
        var command = new OrganizationAdminService.RoleCommand(request.name(), request.description(),
                request.permissions(), request.version());
        return ApiSuccess.of(service.updateRole(principal(authentication), roleId, command,
                metadata(servletRequest)), RequestIds.currentRequestId());
    }

    @PostMapping("/roles/{roleId}/archive")
    ApiSuccess<MessageView> archiveRole(@PathVariable UUID roleId, @RequestParam long version,
                                        Authentication authentication, HttpServletRequest servletRequest) {
        service.archiveRole(principal(authentication), roleId, version, metadata(servletRequest));
        return ApiSuccess.of(new MessageView("Role archived."), RequestIds.currentRequestId());
    }

    @GetMapping("/organization-members")
    ApiSuccess<List<OrganizationMember>> listMembers(Authentication authentication) {
        return ApiSuccess.of(service.listMembers(principal(authentication)), RequestIds.currentRequestId());
    }

    @PostMapping("/organization-members")
    @ResponseStatus(HttpStatus.CREATED)
    ApiSuccess<OrganizationMember> inviteMember(@Valid @RequestBody InviteMemberRequest request,
                                                 Authentication authentication,
                                                 HttpServletRequest servletRequest) {
        var command = new OrganizationAdminService.InviteCommand(request.email(), request.displayName(),
                request.roleId());
        return ApiSuccess.of(service.inviteMember(principal(authentication), command, metadata(servletRequest)),
                RequestIds.currentRequestId());
    }

    @PatchMapping("/organization-members/{membershipId}")
    ApiSuccess<OrganizationMember> updateMember(@PathVariable UUID membershipId,
                                                 @Valid @RequestBody MemberRequest request,
                                                 Authentication authentication,
                                                 HttpServletRequest servletRequest) {
        var command = new OrganizationAdminService.MemberCommand(request.roleId(), request.status(),
                request.version());
        return ApiSuccess.of(service.updateMember(principal(authentication), membershipId, command,
                metadata(servletRequest)), RequestIds.currentRequestId());
    }

    private TenantPrincipal principal(Authentication authentication) {
        return CurrentPrincipal.require(authentication);
    }

    private RequestMetadata metadata(HttpServletRequest request) {
        return RequestMetadata.from(request);
    }

    public record OrganizationRequest(@NotBlank @Size(max = 160) String name, long version) { }
    public record RoleRequest(@NotBlank @Size(max = 100) String name, @Size(max = 240) String description,
                              @NotNull Set<@NotBlank @Size(max = 80) String> permissions, long version) { }
    public record InviteMemberRequest(@NotBlank @Email @Size(max = 254) String email,
                                      @NotBlank @Size(max = 160) String displayName,
                                      @NotNull UUID roleId) { }
    public record MemberRequest(@NotNull UUID roleId, @NotBlank String status, long version) { }
    public record MessageView(String message) { }
}
