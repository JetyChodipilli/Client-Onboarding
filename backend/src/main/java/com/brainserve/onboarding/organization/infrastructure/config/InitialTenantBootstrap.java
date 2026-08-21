package com.brainserve.onboarding.organization.infrastructure.config;

import com.brainserve.onboarding.auth.application.service.PasswordPolicy;
import com.brainserve.onboarding.identity.domain.model.UserAccount;
import com.brainserve.onboarding.identity.application.service.IdentityAccountService;
import com.brainserve.onboarding.organization.domain.model.Organization;
import com.brainserve.onboarding.organization.domain.model.OrganizationMembership;
import com.brainserve.onboarding.organization.domain.model.Role;
import com.brainserve.onboarding.organization.infrastructure.persistence.OrganizationMembershipRepository;
import com.brainserve.onboarding.organization.infrastructure.persistence.OrganizationRepository;
import com.brainserve.onboarding.organization.infrastructure.persistence.RbacJdbcRepository;
import com.brainserve.onboarding.organization.infrastructure.persistence.RoleRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class InitialTenantBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(InitialTenantBootstrap.class);
    private final BootstrapProperties properties;
    private final OrganizationRepository organizations;
    private final IdentityAccountService users;
    private final OrganizationMembershipRepository memberships;
    private final RoleRepository roles;
    private final RbacJdbcRepository rbac;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final Clock clock;

    public InitialTenantBootstrap(BootstrapProperties properties,
                                  OrganizationRepository organizations,
                                  IdentityAccountService users,
                                  OrganizationMembershipRepository memberships,
                                  RoleRepository roles,
                                  RbacJdbcRepository rbac,
                                  JdbcTemplate jdbc,
                                  PasswordEncoder passwordEncoder,
                                  PasswordPolicy passwordPolicy,
                                  Clock clock) {
        this.properties = properties;
        this.organizations = organizations;
        this.users = users;
        this.memberships = memberships;
        this.roles = roles;
        this.rbac = rbac;
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) return;
        requireConfigured();
        String slug = Organization.normalizeSlug(properties.organizationSlug());
        if (organizations.existsBySlugIgnoreCase(slug)) {
            log.info("Initial tenant bootstrap skipped because organization slug already exists");
            return;
        }
        passwordPolicy.validate(properties.adminPassword(), properties.adminEmail());
        Instant now = clock.instant();
        UserAccount user = users.findByNormalizedEmail(UserAccount.normalizeEmail(properties.adminEmail())).orElse(null);
        if (user == null) {
            user = users.saveAndFlush(new UserAccount(UUID.randomUUID(), properties.adminEmail(), properties.adminDisplayName(),
                    passwordEncoder.encode(properties.adminPassword()), now, true));
        } else {
            if (!"ACTIVE".equals(user.getStatus()) || user.getEmailVerifiedAt() == null
                    || !passwordEncoder.matches(properties.adminPassword(), user.getPasswordHash())) {
                throw new IllegalStateException("Bootstrap admin email already exists but cannot be safely reused");
            }
        }
        Organization organization = organizations.saveAndFlush(new Organization(
                UUID.randomUUID(), properties.organizationName(), slug, user.getId(), now));
        OrganizationMembership membership = memberships.saveAndFlush(new OrganizationMembership(
                UUID.randomUUID(), organization.getId(), user.getId(), user.getId(), now));

        Role admin = roles.saveAndFlush(new Role(UUID.randomUUID(), organization.getId(), "ORGANIZATION_ADMIN",
                "Organization Admin", "Full organization administration within this tenant.", true, user.getId(), now));
        Set<UUID> allPermissionIds = new LinkedHashSet<>(jdbc.query(
                "SELECT id FROM client_onboarding.permissions ORDER BY code",
                (rs, rowNum) -> rs.getObject(1, UUID.class)));
        rbac.replaceRolePermissions(organization.getId(), admin.getId(), allPermissionIds, user.getId());
        rbac.replaceMembershipRoles(organization.getId(), membership.getId(), Set.of(admin.getId()), user.getId());
        log.info("Initial tenant bootstrap completed for organization_slug={}", slug);
    }

    private void requireConfigured() {
        if (blank(properties.organizationName()) || blank(properties.organizationSlug()) || blank(properties.adminEmail())
                || blank(properties.adminDisplayName()) || blank(properties.adminPassword())) {
            throw new IllegalStateException("Bootstrap is enabled but APP_BOOTSTRAP_* settings are incomplete");
        }
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
