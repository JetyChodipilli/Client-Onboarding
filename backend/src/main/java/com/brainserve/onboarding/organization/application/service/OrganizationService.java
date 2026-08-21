package com.brainserve.onboarding.organization.application.service;

import com.brainserve.onboarding.audit.application.service.AuditService;
import com.brainserve.onboarding.auth.application.service.SecurityMailService;
import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.common.security.TenantPrincipal;
import com.brainserve.onboarding.common.util.CryptoSupport;
import com.brainserve.onboarding.identity.domain.model.UserAccount;
import com.brainserve.onboarding.identity.application.service.IdentityAccountService;
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
import com.brainserve.onboarding.organization.domain.model.Organization;
import com.brainserve.onboarding.organization.domain.model.OrganizationInvitation;
import com.brainserve.onboarding.organization.domain.model.OrganizationMembership;
import com.brainserve.onboarding.organization.domain.model.Permission;
import com.brainserve.onboarding.organization.domain.model.Role;
import com.brainserve.onboarding.organization.infrastructure.persistence.OrganizationInvitationRepository;
import com.brainserve.onboarding.organization.infrastructure.persistence.OrganizationMembershipRepository;
import com.brainserve.onboarding.organization.infrastructure.persistence.OrganizationRepository;
import com.brainserve.onboarding.organization.infrastructure.persistence.PermissionRepository;
import com.brainserve.onboarding.organization.infrastructure.persistence.RbacJdbcRepository;
import com.brainserve.onboarding.organization.infrastructure.persistence.RoleRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OrganizationService {
    private static final Logger log = LoggerFactory.getLogger(OrganizationService.class);
    private static final Duration INVITATION_TTL = Duration.ofDays(7);
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> ADMIN_RECOVERY_PERMISSIONS = Set.of("USER_MANAGE", "ROLE_MANAGE");

    private final OrganizationRepository organizations;
    private final OrganizationMembershipRepository memberships;
    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final OrganizationInvitationRepository invitations;
    private final IdentityAccountService users;
    private final RbacJdbcRepository rbac;
    private final JdbcTemplate jdbc;
    private final SecurityMailService mailService;
    private final AuditService audit;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public OrganizationService(OrganizationRepository organizations,
                               OrganizationMembershipRepository memberships,
                               RoleRepository roles,
                               PermissionRepository permissions,
                               OrganizationInvitationRepository invitations,
                               IdentityAccountService users,
                               RbacJdbcRepository rbac,
                               JdbcTemplate jdbc,
                               SecurityMailService mailService,
                               AuditService audit,
                               Clock clock,
                               TransactionTemplate transactionTemplate) {
        this.organizations = organizations;
        this.memberships = memberships;
        this.roles = roles;
        this.permissions = permissions;
        this.invitations = invitations;
        this.users = users;
        this.rbac = rbac;
        this.jdbc = jdbc;
        this.mailService = mailService;
        this.audit = audit;
        this.clock = clock;
        this.transactionTemplate = transactionTemplate;
    }

    @Transactional(readOnly = true)
    public OrganizationResponse getOrganization(TenantPrincipal principal, UUID requestedOrganizationId) {
        requireCurrentTenant(principal, requestedOrganizationId);
        Organization org = organizations.findById(principal.organizationId()).orElseThrow(OrganizationService::notFound);
        return map(org);
    }

    @Transactional
    public OrganizationResponse updateOrganization(TenantPrincipal principal, UUID requestedOrganizationId,
                                                   UpdateOrganizationRequest request, HttpServletRequest servletRequest) {
        requireCurrentTenant(principal, requestedOrganizationId);
        Organization org = organizations.findById(principal.organizationId()).orElseThrow(OrganizationService::notFound);
        if (org.getVersion() != request.version()) throw conflict();
        Map<String, Object> before = Map.of("name", org.getName(), "version", org.getVersion());
        org.updateName(request.name(), principal.userId(), clock.instant());
        organizations.saveAndFlush(org);
        audit.record(principal.organizationId(), principal.userId(), "ORGANIZATION_UPDATED", "ORGANIZATION",
                org.getId(), before, Map.of("name", org.getName(), "version", org.getVersion()), servletRequest);
        return map(org);
    }

    @Transactional(readOnly = true)
    public PageResult<OrganizationUserResponse> listUsers(TenantPrincipal principal, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        long offset = (long) safePage * safeSize;
        Long totalValue = jdbc.queryForObject("""
                SELECT COUNT(*) FROM client_onboarding.organization_users
                WHERE organization_id = ? AND status <> 'REMOVED'
                """, Long.class, principal.organizationId());
        long total = totalValue == null ? 0 : totalValue;
        List<UserRow> rows = jdbc.query("""
                SELECT m.id AS membership_id, m.user_id, m.status, m.version,
                       u.email, u.display_name
                FROM client_onboarding.organization_users m
                JOIN client_onboarding.users u ON u.id = m.user_id
                WHERE m.organization_id = ? AND m.status <> 'REMOVED'
                ORDER BY u.display_name, u.email, m.id
                LIMIT ? OFFSET ?
                """, (rs, rowNum) -> new UserRow(
                        rs.getObject("membership_id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getString("email"), rs.getString("display_name"), rs.getString("status"),
                        rs.getLong("version")),
                principal.organizationId(), safeSize, offset);
        Map<UUID, List<OrganizationUserResponse.RoleSummary>> roleMap = roleSummariesForMemberships(
                principal.organizationId(), rows.stream().map(UserRow::membershipId).toList());
        List<OrganizationUserResponse> items = rows.stream().map(row -> new OrganizationUserResponse(
                row.membershipId(), row.userId(), row.email(), row.displayName(), row.status(),
                roleMap.getOrDefault(row.membershipId(), List.of()), row.version())).toList();
        return new PageResult<>(items, safePage, safeSize, total);
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> listPermissions() {
        return permissions.findAllByOrderByCategoryAscCodeAsc().stream().map(this::map).toList();
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> listRoles(TenantPrincipal principal) {
        Map<UUID, Set<String>> byRole = rolePermissionMap(principal.organizationId());
        return roles.findAllByOrganizationIdAndStatusOrderByNameAsc(principal.organizationId(), "ACTIVE")
                .stream().map(role -> map(role, byRole.getOrDefault(role.getId(), Set.of()))).toList();
    }

    @Transactional
    public RoleResponse createRole(TenantPrincipal principal, CreateRoleRequest request, HttpServletRequest servletRequest) {
        ensurePermissionsExist(request.permissionIds());
        Set<String> requestedPermissions = permissionCodes(request.permissionIds());
        ensureDelegablePermissions(principal, requestedPermissions);
        String code = Role.normalizeCode(request.code());
        if (roles.existsByOrganizationIdAndCode(principal.organizationId(), code)) {
            throw new ApiException(HttpStatus.CONFLICT, "ROLE_CODE_EXISTS", "A role with that code already exists.");
        }
        Instant now = clock.instant();
        Role role = new Role(UUID.randomUUID(), principal.organizationId(), code, request.name(), request.description(),
                false, principal.userId(), now);
        roles.saveAndFlush(role);
        rbac.replaceRolePermissions(principal.organizationId(), role.getId(), request.permissionIds(), principal.userId());
        Set<String> codes = requestedPermissions;
        audit.record(principal.organizationId(), principal.userId(), "ROLE_CREATED", "ROLE", role.getId(), null,
                Map.of("code", role.getCode(), "name", role.getName(), "permissions", codes), servletRequest);
        return map(role, codes);
    }

    @Transactional
    public RoleResponse updateRole(TenantPrincipal principal, UUID roleId, UpdateRoleRequest request,
                                   HttpServletRequest servletRequest) {
        lockOrganization(principal.organizationId());
        ensurePermissionsExist(request.permissionIds());
        Set<String> requestedPermissions = permissionCodes(request.permissionIds());
        Role role = roles.findByOrganizationIdAndIdAndStatus(principal.organizationId(), roleId, "ACTIVE")
                .orElseThrow(OrganizationService::notFound);
        if (role.getVersion() != request.version()) throw conflict();
        if (role.isSystemRole()) {
            throw new ApiException(HttpStatus.CONFLICT, "SYSTEM_ROLE_IMMUTABLE", "System roles cannot be modified.");
        }
        Set<String> previousPermissions = rbac.permissionCodesForRole(principal.organizationId(), roleId);
        ensureDelegableAdditions(principal, previousPermissions, requestedPermissions);
        for (String permission : ADMIN_RECOVERY_PERMISSIONS) {
            if (previousPermissions.contains(permission) && !requestedPermissions.contains(permission)
                    && rbac.countActiveMembersWithPermissionExcludingRole(principal.organizationId(), permission, roleId) == 0) {
                throw lastAdminProtected(permission);
            }
        }
        Map<String, Object> before = Map.of("name", role.getName(), "permissions", previousPermissions, "version", role.getVersion());
        role.update(request.name(), request.description(), principal.userId(), clock.instant());
        roles.saveAndFlush(role);
        rbac.replaceRolePermissions(principal.organizationId(), roleId, request.permissionIds(), principal.userId());
        Set<String> codes = requestedPermissions;
        audit.record(principal.organizationId(), principal.userId(), "ROLE_UPDATED", "ROLE", roleId, before,
                Map.of("name", role.getName(), "permissions", codes, "version", role.getVersion()), servletRequest);
        return map(role, codes);
    }

    public InvitationResponse inviteUser(TenantPrincipal principal, InviteOrganizationUserRequest request,
                                         HttpServletRequest servletRequest) {
        PendingInvitationDispatch dispatch = transactionTemplate.execute(status ->
                createInvitation(principal, request, servletRequest));
        if (dispatch == null) throw new IllegalStateException("Invitation transaction produced no result");

        try {
            mailService.sendOrganizationInvitation(dispatch.email(), dispatch.organizationName(), dispatch.rawToken());
        } catch (MailException ex) {
            log.error("Organization invitation delivery failed for invitation_id={}", dispatch.response().id(), ex);
            transactionTemplate.executeWithoutResult(status -> audit.record(
                    principal.organizationId(), principal.userId(), "ORGANIZATION_INVITATION_DELIVERY_FAILED",
                    "ORGANIZATION_INVITATION", dispatch.response().id(), null,
                    Map.of("email", UserAccount.normalizeEmail(dispatch.email())), servletRequest));
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "INVITATION_DELIVERY_FAILED",
                    "The invitation was created but could not be delivered. Retry the invitation when mail is available.");
        }
        return dispatch.response();
    }

    private PendingInvitationDispatch createInvitation(TenantPrincipal principal, InviteOrganizationUserRequest request,
                                                        HttpServletRequest servletRequest) {
        // Serialize invitation creation within the tenant so two concurrent requests cannot create
        // multiple active invitation tokens for the same address or race administrator mutations.
        lockOrganization(principal.organizationId());
        ensureTenantRoles(principal.organizationId(), request.roleIds());
        ensureDelegablePermissions(principal, rbac.permissionCodesForRoles(principal.organizationId(), request.roleIds()));
        String normalizedEmail = UserAccount.normalizeEmail(request.email());
        users.findByNormalizedEmail(normalizedEmail).ifPresent(user -> {
            if (memberships.findByOrganizationIdAndUserId(principal.organizationId(), user.getId()).isPresent()) {
                throw new ApiException(HttpStatus.CONFLICT, "USER_ALREADY_MEMBER", "This user already belongs to the organization.");
            }
        });

        Instant now = clock.instant();
        invitations.findFirstByOrganizationIdAndNormalizedEmailAndStatusOrderByCreatedAtDesc(
                principal.organizationId(), normalizedEmail, "PENDING").ifPresent(old -> {
                    old.revoke(now);
                    // Flush the revoked invitation before inserting a replacement so the partial unique
                    // index on pending invitations is never transiently violated by Hibernate flush ordering.
                    invitations.saveAndFlush(old);
                });

        String rawToken = CryptoSupport.randomUrlToken(32);
        OrganizationInvitation invitation = new OrganizationInvitation(
                UUID.randomUUID(), principal.organizationId(), request.email(), normalizedEmail, request.displayName(),
                CryptoSupport.sha256Hex(rawToken), now.plus(INVITATION_TTL), principal.userId(), now);
        invitations.saveAndFlush(invitation);
        for (UUID roleId : request.roleIds()) rbac.addInvitationRole(principal.organizationId(), invitation.getId(), roleId);
        Organization organization = organizations.findById(principal.organizationId()).orElseThrow(OrganizationService::notFound);
        audit.record(principal.organizationId(), principal.userId(), "ORGANIZATION_USER_INVITED", "ORGANIZATION_INVITATION",
                invitation.getId(), null, Map.of("email", normalizedEmail, "roles", request.roleIds()), servletRequest);
        return new PendingInvitationDispatch(map(invitation), request.email(), organization.getName(), rawToken);
    }

    @Transactional
    public OrganizationUserResponse assignRoles(TenantPrincipal principal, UUID membershipId, AssignRolesRequest request,
                                                HttpServletRequest servletRequest) {
        lockOrganization(principal.organizationId());
        ensureTenantRoles(principal.organizationId(), request.roleIds());
        Set<String> requestedRolePermissions = rbac.permissionCodesForRoles(principal.organizationId(), request.roleIds());
        OrganizationMembership membership = memberships.findByOrganizationIdAndId(principal.organizationId(), membershipId)
                .orElseThrow(OrganizationService::notFound);
        if (membership.getVersion() != request.version()) throw conflict();
        List<UUID> beforeRoles = rbac.roleIds(principal.organizationId(), membershipId);
        Set<String> beforePermissions = rbac.permissionCodes(principal.organizationId(), membershipId);
        Set<String> afterPermissions = requestedRolePermissions;
        ensureDelegableAdditions(principal, beforePermissions, afterPermissions);
        for (String permission : ADMIN_RECOVERY_PERMISSIONS) {
            if ("ACTIVE".equals(membership.getStatus()) && beforePermissions.contains(permission) && !afterPermissions.contains(permission)
                    && rbac.countActiveMembersWithPermission(principal.organizationId(), permission) <= 1) {
                throw lastAdminProtected(permission);
            }
        }
        rbac.replaceMembershipRoles(principal.organizationId(), membershipId, request.roleIds(), principal.userId());
        membership.markRolesChanged(principal.userId(), clock.instant());
        memberships.saveAndFlush(membership);
        audit.record(principal.organizationId(), principal.userId(), "ORGANIZATION_USER_ROLES_CHANGED", "ORGANIZATION_USER",
                membershipId, Map.of("roleIds", beforeRoles), Map.of("roleIds", request.roleIds()), servletRequest);
        return loadUser(principal.organizationId(), membershipId);
    }

    @Transactional
    public OrganizationUserResponse updateMembershipStatus(TenantPrincipal principal, UUID membershipId,
                                                            UpdateMembershipStatusRequest request,
                                                            HttpServletRequest servletRequest) {
        lockOrganization(principal.organizationId());
        OrganizationMembership membership = memberships.findByOrganizationIdAndId(principal.organizationId(), membershipId)
                .orElseThrow(OrganizationService::notFound);
        if (membership.getVersion() != request.version()) throw conflict();
        if (membership.getUserId().equals(principal.userId()) && "SUSPENDED".equals(request.status())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SELF_SUSPENSION_NOT_ALLOWED", "You cannot suspend your own membership.");
        }
        String before = membership.getStatus();
        if ("ACTIVE".equals(membership.getStatus()) && "SUSPENDED".equals(request.status())) {
            Set<String> currentPermissions = rbac.permissionCodes(principal.organizationId(), membershipId);
            for (String permission : ADMIN_RECOVERY_PERMISSIONS) {
                if (currentPermissions.contains(permission)
                        && rbac.countActiveMembersWithPermission(principal.organizationId(), permission) <= 1) {
                    throw lastAdminProtected(permission);
                }
            }
        }
        if ("ACTIVE".equals(request.status())) membership.activate(principal.userId(), clock.instant());
        else membership.suspend(principal.userId(), clock.instant());
        memberships.saveAndFlush(membership);
        audit.record(principal.organizationId(), principal.userId(), "ORGANIZATION_USER_STATUS_CHANGED", "ORGANIZATION_USER",
                membershipId, Map.of("status", before), Map.of("status", membership.getStatus()), servletRequest);
        return loadUser(principal.organizationId(), membershipId);
    }

    private OrganizationUserResponse loadUser(UUID organizationId, UUID membershipId) {
        return jdbc.query("""
                SELECT m.id AS membership_id, m.user_id, m.status, m.version, u.email, u.display_name
                FROM client_onboarding.organization_users m JOIN client_onboarding.users u ON u.id = m.user_id
                WHERE m.organization_id = ? AND m.id = ?
                """, rs -> rs.next() ? new OrganizationUserResponse(
                        rs.getObject("membership_id", UUID.class), rs.getObject("user_id", UUID.class),
                        rs.getString("email"), rs.getString("display_name"), rs.getString("status"),
                        roleSummaries(organizationId, membershipId), rs.getLong("version")) : null,
                organizationId, membershipId);
    }

    private List<OrganizationUserResponse.RoleSummary> roleSummaries(UUID organizationId, UUID membershipId) {
        return jdbc.query("""
                SELECT r.id, r.code, r.name
                FROM client_onboarding.organization_user_roles our
                JOIN client_onboarding.roles r ON r.id = our.role_id AND r.organization_id = our.organization_id
                WHERE our.organization_id = ? AND our.organization_user_id = ? AND r.status = 'ACTIVE'
                ORDER BY r.name
                """, (rs, rowNum) -> new OrganizationUserResponse.RoleSummary(
                        rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name")),
                organizationId, membershipId);
    }

    private Map<UUID, List<OrganizationUserResponse.RoleSummary>> roleSummariesForMemberships(
            UUID organizationId, List<UUID> membershipIds) {
        if (membershipIds.isEmpty()) return Map.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(membershipIds.size(), "?"));
        Object[] args = new Object[membershipIds.size() + 1];
        args[0] = organizationId;
        for (int i = 0; i < membershipIds.size(); i++) args[i + 1] = membershipIds.get(i);
        Map<UUID, List<OrganizationUserResponse.RoleSummary>> result = new LinkedHashMap<>();
        String sql = """
                SELECT our.organization_user_id, r.id, r.code, r.name
                FROM client_onboarding.organization_user_roles our
                JOIN client_onboarding.roles r
                  ON r.id = our.role_id AND r.organization_id = our.organization_id
                WHERE our.organization_id = ? AND r.status = 'ACTIVE'
                  AND our.organization_user_id IN (%s)
                ORDER BY our.organization_user_id, r.name, r.id
                """.formatted(placeholders);
        jdbc.query(sql,
                rs -> {
                    UUID membershipId = rs.getObject("organization_user_id", UUID.class);
                    result.computeIfAbsent(membershipId, ignored -> new java.util.ArrayList<>()).add(
                            new OrganizationUserResponse.RoleSummary(rs.getObject("id", UUID.class),
                                    rs.getString("code"), rs.getString("name")));
                }, args);
        return result;
    }

    private Map<UUID, Set<String>> rolePermissionMap(UUID organizationId) {
        Map<UUID, Set<String>> result = new LinkedHashMap<>();
        for (RbacJdbcRepository.RolePermissionRow row : rbac.rolePermissions(organizationId)) {
            result.computeIfAbsent(row.roleId(), ignored -> new LinkedHashSet<>());
            if (row.permissionCode() != null) result.get(row.roleId()).add(row.permissionCode());
        }
        return result;
    }

    private Set<String> permissionCodes(Set<UUID> ids) {
        if (ids.isEmpty()) return Set.of();
        Map<UUID, String> map = new HashMap<>();
        for (Permission permission : permissions.findAllById(ids)) map.put(permission.getId(), permission.getCode());
        Set<String> result = new LinkedHashSet<>();
        for (UUID id : ids) result.add(map.get(id));
        return result;
    }

    private void ensurePermissionsExist(Set<UUID> ids) {
        if (rbac.countPermissionIds(ids) != ids.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PERMISSION", "One or more permissions are invalid.");
        }
    }

    private void ensureTenantRoles(UUID organizationId, Set<UUID> roleIds) {
        if (rbac.countTenantRoles(organizationId, roleIds) != roleIds.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ROLE", "One or more roles are invalid for this organization.");
        }
    }


    private static void ensureDelegablePermissions(TenantPrincipal principal, Set<String> requestedPermissions) {
        if (!principal.permissions().containsAll(requestedPermissions)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PERMISSION_DELEGATION_FORBIDDEN",
                    "You cannot grant permissions that you do not currently hold.");
        }
    }

    private static void ensureDelegableAdditions(TenantPrincipal principal,
                                                   Set<String> currentPermissions,
                                                   Set<String> requestedPermissions) {
        Set<String> additions = new LinkedHashSet<>(requestedPermissions);
        additions.removeAll(currentPermissions);
        ensureDelegablePermissions(principal, additions);
    }

    private void lockOrganization(UUID organizationId) {
        UUID locked = jdbc.query("SELECT id FROM client_onboarding.organizations WHERE id = ? FOR UPDATE",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null, organizationId);
        if (locked == null) throw notFound();
    }

    private static ApiException lastAdminProtected(String permission) {
        return new ApiException(HttpStatus.CONFLICT, "LAST_ADMIN_PROTECTED",
                "At least one active administrator with " + permission + " must remain.");
    }

    private static void requireCurrentTenant(TenantPrincipal principal, UUID requestedOrganizationId) {
        if (!principal.organizationId().equals(requestedOrganizationId)) throw notFound();
    }

    private static OrganizationResponse map(Organization org) {
        return new OrganizationResponse(org.getId(), org.getName(), org.getSlug(), org.getStatus(), org.getVersion());
    }

    private PermissionResponse map(Permission permission) {
        return new PermissionResponse(permission.getId(), permission.getCode(), permission.getCategory(), permission.getDescription());
    }

    private static RoleResponse map(Role role, Set<String> permissionCodes) {
        return new RoleResponse(role.getId(), role.getCode(), role.getName(), role.getDescription(), role.isSystemRole(),
                permissionCodes, role.getVersion());
    }

    private static InvitationResponse map(OrganizationInvitation invitation) {
        return new InvitationResponse(invitation.getId(), invitation.getEmail(), invitation.getDisplayName(),
                invitation.getStatus(), invitation.getExpiresAt());
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Requested resource was not found.");
    }

    private static ApiException conflict() {
        return new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "The resource changed. Refresh and try again.");
    }

    private record PendingInvitationDispatch(InvitationResponse response, String email, String organizationName, String rawToken) {}

    private record UserRow(UUID membershipId, UUID userId, String email, String displayName, String status, long version) {}

    public record PageResult<T>(List<T> items, int page, int size, long totalElements) {
        public PageResult { items = List.copyOf(items); }
        public int totalPages() { return size == 0 ? 0 : (int) Math.ceil((double) totalElements / size); }
    }
}
