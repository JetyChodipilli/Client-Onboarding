package com.brainserve.clientonboarding.auth.infrastructure.configuration;

import com.brainserve.clientonboarding.audit.application.AuditService;
import com.brainserve.clientonboarding.auth.application.PasswordPolicy;
import com.brainserve.clientonboarding.identity.domain.model.UserAccount;
import com.brainserve.clientonboarding.identity.domain.repository.IdentityRepository;
import com.brainserve.clientonboarding.organization.domain.model.Organization;
import com.brainserve.clientonboarding.organization.domain.model.OrganizationAccess;
import com.brainserve.clientonboarding.organization.domain.model.OrganizationMember;
import com.brainserve.clientonboarding.organization.domain.model.Role;
import com.brainserve.clientonboarding.organization.domain.repository.OrganizationAdminRepository;
import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(prefix = "app.security.bootstrap", name = "enabled", havingValue = "true")
public class BootstrapProvisioner implements ApplicationRunner {
    private final AuthProperties properties;
    private final OrganizationAdminRepository organizations;
    private final IdentityRepository identities;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final Clock clock;

    public BootstrapProvisioner(AuthProperties properties, OrganizationAdminRepository organizations,
                                IdentityRepository identities, PasswordPolicy passwordPolicy,
                                PasswordEncoder passwordEncoder, AuditService audit, Clock clock) {
        this.properties = properties;
        this.organizations = organizations;
        this.identities = identities;
        this.passwordPolicy = passwordPolicy;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AuthProperties.Bootstrap config = properties.bootstrap();
        String slug = required(config.organizationSlug(), "APP_BOOTSTRAP_ORGANIZATION_SLUG")
                .toLowerCase(Locale.ROOT);
        if (!slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*") || slug.length() > 80) {
            throw new IllegalStateException("Bootstrap organization slug is invalid");
        }
        if (organizations.findOrganizationBySlug(slug).isPresent()) return;

        String password = required(config.adminPassword(), "APP_BOOTSTRAP_ADMIN_PASSWORD");
        passwordPolicy.validate(password);
        String email = required(config.adminEmail(), "APP_BOOTSTRAP_ADMIN_EMAIL")
                .trim().toLowerCase(Locale.ROOT);
        if (identities.findByEmail(email).isPresent()) {
            throw new IllegalStateException("Bootstrap admin email is already registered");
        }
        var now = clock.instant();
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        String organizationName = required(config.organizationName(), "APP_BOOTSTRAP_ORGANIZATION_NAME").trim();
        String adminName = required(config.adminName(), "APP_BOOTSTRAP_ADMIN_NAME").trim();

        organizations.insertOrganization(new Organization(organizationId, slug, organizationName,
                Organization.OrganizationStatus.ACTIVE, now, now, 0), userId);
        identities.insert(new UserAccount(userId, email, adminName, passwordEncoder.encode(password),
                UserAccount.PrincipalType.INTERNAL, UserAccount.UserStatus.ACTIVE, now, 0, null, 0, 0),
                now, userId);
        Set<String> permissions = Set.copyOf(organizations.listPermissionCodes());
        organizations.insertRole(new Role(roleId, organizationId, "Organization Administrator",
                "Bootstrap role with every registered permission.", permissions, null, 0), userId, now);
        organizations.insertMember(new OrganizationMember(UUID.randomUUID(), organizationId, userId, email,
                adminName, roleId, "Organization Administrator", OrganizationAccess.MembershipStatus.ACTIVE,
                now, now, 0), userId, now);
        audit.append(organizationId, userId, "ORGANIZATION_BOOTSTRAPPED", "ORGANIZATION", organizationId,
                Map.of(), Map.of("slug", slug), "SYSTEM", null);
    }

    private String required(String value, String variable) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(variable + " is required when bootstrap is enabled");
        }
        return value;
    }
}
