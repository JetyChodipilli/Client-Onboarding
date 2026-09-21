package com.brainserve.clientonboarding.organization.application;

import com.brainserve.clientonboarding.audit.application.AuditService;
import com.brainserve.clientonboarding.common.observability.RequestMetadata;
import com.brainserve.clientonboarding.common.security.TenantPrincipal;
import com.brainserve.clientonboarding.common.error.DomainException;
import com.brainserve.clientonboarding.identity.domain.model.UserAccount;
import com.brainserve.clientonboarding.identity.domain.repository.IdentityRepository;
import com.brainserve.clientonboarding.organization.domain.model.Organization;
import com.brainserve.clientonboarding.organization.domain.model.OrganizationAccess;
import com.brainserve.clientonboarding.organization.domain.model.OrganizationMember;
import com.brainserve.clientonboarding.organization.domain.model.Role;
import com.brainserve.clientonboarding.organization.domain.repository.OrganizationAdminRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationAdminService {
    private static final Set<String> MANAGER_PERMISSIONS = Set.of("USER_MANAGE", "ROLE_MANAGE");

    private final OrganizationAdminRepository organizations;
    private final IdentityRepository identities;
    private final PasswordEncoder passwordEncoder;
    private final OrganizationSecurityPort security;
    private final AuditService audit;
    private final Clock clock;

    public OrganizationAdminService(OrganizationAdminRepository organizations,
                                    IdentityRepository identities,
                                    PasswordEncoder passwordEncoder,
                                    OrganizationSecurityPort security,
                                    AuditService audit,
                                    Clock clock) {
        this.organizations = organizations;
        this.identities = identities;
        this.passwordEncoder = passwordEncoder;
        this.security = security;
        this.audit = audit;
        this.clock = clock;
    }

    @PreAuthorize("hasAuthority('ORGANIZATION_READ')")
    public Organization getOrganization(TenantPrincipal principal, UUID organizationId) {
        return organizations.findOrganizationByIdAndTenant(organizationId, principal.organizationId())
                .orElseThrow(this::notFound);
    }

    @PreAuthorize("hasAuthority('ORGANIZATION_UPDATE')")
    @Transactional
    public Organization updateOrganization(TenantPrincipal principal, UUID organizationId,
                                             String name, long version, RequestMetadata metadata) {
        Organization current = getOrganizationForMutation(principal, organizationId);
        Instant now = clock.instant();
        if (!organizations.updateOrganization(organizationId, principal.organizationId(), cleanName(name, 160),
                version, principal.userId(), now)) throw conflict();
        audit.append(principal.organizationId(), principal.userId(), "ORGANIZATION_UPDATED", "ORGANIZATION",
                organizationId, Map.of("name", current.name()), Map.of("name", name), "API", metadata.ipHash());
        return organizations.findOrganizationByIdAndTenant(organizationId, principal.organizationId()).orElseThrow();
    }

    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public List<String> listPermissions() {
        return organizations.listPermissionCodes();
    }

    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public List<Role> listRoles(TenantPrincipal principal) {
        return organizations.listRoles(principal.organizationId());
    }

    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    @Transactional
    public Role createRole(TenantPrincipal principal, RoleCommand command, RequestMetadata metadata) {
        Set<String> permissions = validatePermissions(command.permissions());
        Instant now = clock.instant();
        Role role = new Role(UUID.randomUUID(), principal.organizationId(), cleanName(command.name(), 100),
                cleanDescription(command.description()), permissions, null, 0);
        organizations.insertRole(role, principal.userId(), now);
        audit.append(principal.organizationId(), principal.userId(), "ROLE_CREATED", "ROLE", role.id(),
                Map.of(), Map.of("name", role.name(), "permissions", permissions), "API", metadata.ipHash());
        return organizations.findRole(principal.organizationId(), role.id()).orElseThrow();
    }

    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    @Transactional
    public Role updateRole(TenantPrincipal principal, UUID roleId, RoleCommand command,
                           RequestMetadata metadata) {
        Role current = organizations.findRole(principal.organizationId(), roleId).orElseThrow(this::notFound);
        Set<String> permissions = validatePermissions(command.permissions());
        preventLastManagerRoleLoss(principal.organizationId(), current, permissions);
        Instant now = clock.instant();
        if (!organizations.updateRole(principal.organizationId(), roleId, cleanName(command.name(), 100),
                cleanDescription(command.description()), permissions, command.version(), principal.userId(), now)) {
            throw conflict();
        }
        audit.append(principal.organizationId(), principal.userId(), "ROLE_UPDATED", "ROLE", roleId,
                Map.of("name", current.name(), "permissions", current.permissions()),
                Map.of("name", command.name(), "permissions", permissions), "API", metadata.ipHash());
        return organizations.findRole(principal.organizationId(), roleId).orElseThrow();
    }

    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    @Transactional
    public void archiveRole(TenantPrincipal principal, UUID roleId, long version, RequestMetadata metadata) {
        Role current = organizations.findRole(principal.organizationId(), roleId).orElseThrow(this::notFound);
        if (current.permissions().containsAll(MANAGER_PERMISSIONS)
                && organizations.countActiveManagers(principal.organizationId(), "USER_MANAGE", "ROLE_MANAGE")
                <= organizations.countActiveMembersForRole(principal.organizationId(), roleId)) {
            throw lastManager();
        }
        if (!organizations.archiveRole(principal.organizationId(), roleId, version, principal.userId(), clock.instant())) {
            throw new DomainException("ROLE_IN_USE_OR_STALE",
                    "The role is assigned to a member or was changed by another request.", HttpStatus.CONFLICT);
        }
        audit.append(principal.organizationId(), principal.userId(), "ROLE_ARCHIVED", "ROLE", roleId,
                Map.of("name", current.name()), Map.of("archived", true), "API", metadata.ipHash());
    }

    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public List<OrganizationMember> listMembers(TenantPrincipal principal) {
        return organizations.listMembers(principal.organizationId());
    }

    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Transactional
    public OrganizationMember inviteMember(TenantPrincipal principal, InviteCommand command,
                                             RequestMetadata metadata) {
        Role role = organizations.findRole(principal.organizationId(), command.roleId())
                .filter(value -> value.archivedAt() == null).orElseThrow(this::notFound);
        Instant now = clock.instant();
        String email = normalizeEmail(command.email());
        UserAccount user = identities.findByEmail(email).orElseGet(() -> {
            var created = new UserAccount(UUID.randomUUID(), email, cleanName(command.displayName(), 160),
                    passwordEncoder.encode(UUID.randomUUID().toString()), UserAccount.PrincipalType.INTERNAL,
                    UserAccount.UserStatus.PENDING, null, 0, null, 0, 0);
            identities.insert(created, now, principal.userId());
            return created;
        });
        if (user.principalType() != UserAccount.PrincipalType.INTERNAL
                || user.status() == UserAccount.UserStatus.ARCHIVED) {
            throw new DomainException("IDENTITY_CONTEXT_CONFLICT",
                    "This email cannot be invited to the internal workspace.", HttpStatus.CONFLICT);
        }
        OrganizationMember member = new OrganizationMember(UUID.randomUUID(), principal.organizationId(),
                user.id(), user.email(), user.displayName(), role.id(), role.name(),
                OrganizationAccess.MembershipStatus.INVITED, now, null, 0);
        organizations.insertMember(member, principal.userId(), now);
        String organizationName = organizations.findOrganizationByIdAndTenant(
                principal.organizationId(), principal.organizationId()).orElseThrow().name();
        security.issueInvitation(principal.organizationId(), member.id(), user, organizationName, now);
        audit.append(principal.organizationId(), principal.userId(), "ORGANIZATION_MEMBER_INVITED",
                "ORGANIZATION_MEMBERSHIP", member.id(), Map.of(),
                Map.of("userId", user.id(), "roleId", role.id()), "API", metadata.ipHash());
        return member;
    }

    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Transactional
    public OrganizationMember updateMember(TenantPrincipal principal, UUID membershipId,
                                             MemberCommand command, RequestMetadata metadata) {
        OrganizationMember current = organizations.findMember(principal.organizationId(), membershipId)
                .orElseThrow(this::notFound);
        Role currentRole = organizations.findRole(principal.organizationId(), current.roleId())
                .orElseThrow(this::notFound);
        Role nextRole = organizations.findRole(principal.organizationId(), command.roleId())
                .filter(value -> value.archivedAt() == null).orElseThrow(this::notFound);
        OrganizationAccess.MembershipStatus nextStatus = parseStatus(command.status());
        boolean losesManagement = current.status() == OrganizationAccess.MembershipStatus.ACTIVE
                && currentRole.permissions().containsAll(MANAGER_PERMISSIONS)
                && (nextStatus != OrganizationAccess.MembershipStatus.ACTIVE
                    || !nextRole.permissions().containsAll(MANAGER_PERMISSIONS));
        if (losesManagement && organizations.countActiveManagers(principal.organizationId(),
                "USER_MANAGE", "ROLE_MANAGE") <= 1) throw lastManager();
        if (!organizations.updateMember(principal.organizationId(), membershipId, command.roleId(),
                nextStatus.name(), command.version(), principal.userId(), clock.instant())) throw conflict();
        if (nextStatus != OrganizationAccess.MembershipStatus.ACTIVE) {
            security.revokeUserSessions(current.userId(), clock.instant());
        }
        audit.append(principal.organizationId(), principal.userId(), "ORGANIZATION_MEMBER_UPDATED",
                "ORGANIZATION_MEMBERSHIP", membershipId,
                Map.of("roleId", current.roleId(), "status", current.status()),
                Map.of("roleId", nextRole.id(), "status", nextStatus), "API", metadata.ipHash());
        return organizations.findMember(principal.organizationId(), membershipId).orElseThrow();
    }

    private Organization getOrganizationForMutation(TenantPrincipal principal, UUID id) {
        return organizations.findOrganizationByIdAndTenant(id, principal.organizationId()).orElseThrow(this::notFound);
    }

    private void preventLastManagerRoleLoss(UUID organizationId, Role current, Set<String> replacement) {
        if (current.permissions().containsAll(MANAGER_PERMISSIONS) && !replacement.containsAll(MANAGER_PERMISSIONS)) {
            long total = organizations.countActiveManagers(organizationId, "USER_MANAGE", "ROLE_MANAGE");
            long affected = organizations.countActiveMembersForRole(organizationId, current.id());
            if (total <= affected) throw lastManager();
        }
    }

    private Set<String> validatePermissions(Set<String> values) {
        Set<String> permissions = values == null ? Set.of() : Set.copyOf(values);
        if (!Set.copyOf(organizations.listPermissionCodes()).containsAll(permissions)) {
            throw new DomainException("INVALID_PERMISSION", "One or more permissions are not recognized.",
                    HttpStatus.BAD_REQUEST);
        }
        return permissions;
    }

    private OrganizationAccess.MembershipStatus parseStatus(String value) {
        try {
            var status = OrganizationAccess.MembershipStatus.valueOf(value.toUpperCase(Locale.ROOT));
            if (status == OrganizationAccess.MembershipStatus.INVITED) throw new IllegalArgumentException();
            return status;
        } catch (RuntimeException exception) {
            throw new DomainException("INVALID_MEMBERSHIP_STATUS",
                    "Membership status must be ACTIVE, SUSPENDED, or ARCHIVED.", HttpStatus.BAD_REQUEST);
        }
    }

    private String cleanName(String value, int max) {
        String clean = value == null ? "" : value.trim();
        if (clean.isEmpty() || clean.length() > max) {
            throw new DomainException("VALIDATION_FAILED", "A valid name is required.", HttpStatus.BAD_REQUEST);
        }
        return clean;
    }

    private String cleanDescription(String value) {
        String clean = value == null ? "" : value.trim();
        if (clean.length() > 240) {
            throw new DomainException("VALIDATION_FAILED", "Description must be 240 characters or fewer.",
                    HttpStatus.BAD_REQUEST);
        }
        return clean;
    }

    private String normalizeEmail(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private DomainException notFound() {
        return new DomainException("RESOURCE_NOT_FOUND", "Requested resource was not found.", HttpStatus.NOT_FOUND);
    }

    private DomainException conflict() {
        return new DomainException("OPTIMISTIC_LOCK_CONFLICT",
                "The resource changed. Refresh and try again.", HttpStatus.CONFLICT);
    }

    private DomainException lastManager() {
        return new DomainException("LAST_MANAGER_REQUIRED",
                "At least one active member must retain user and role management permissions.", HttpStatus.CONFLICT);
    }

    public record RoleCommand(String name, String description, Set<String> permissions, long version) { }
    public record InviteCommand(String email, String displayName, UUID roleId) { }
    public record MemberCommand(UUID roleId, String status, long version) { }
}
