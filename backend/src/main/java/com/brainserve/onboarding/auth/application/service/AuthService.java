package com.brainserve.onboarding.auth.application.service;

import com.brainserve.onboarding.audit.application.service.AuditService;
import com.brainserve.onboarding.auth.api.request.AcceptInvitationRequest;
import com.brainserve.onboarding.auth.api.request.ChangePasswordRequest;
import com.brainserve.onboarding.auth.api.request.LoginRequest;
import com.brainserve.onboarding.auth.api.response.AuthResponse;
import com.brainserve.onboarding.auth.api.response.AuthUserResponse;
import com.brainserve.onboarding.auth.api.response.MfaSetupResponse;
import com.brainserve.onboarding.auth.domain.model.AuthSession;
import com.brainserve.onboarding.auth.domain.model.SecurityToken;
import com.brainserve.onboarding.auth.infrastructure.persistence.AuthSessionRepository;
import com.brainserve.onboarding.auth.infrastructure.persistence.ClientAuthSessionRepository;
import com.brainserve.onboarding.auth.infrastructure.persistence.SecurityTokenRepository;
import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.common.security.FieldEncryptionService;
import com.brainserve.onboarding.common.security.NetworkSource;
import com.brainserve.onboarding.common.security.PrivilegedPermissionPolicy;
import com.brainserve.onboarding.common.security.SecurityProperties;
import com.brainserve.onboarding.common.security.TenantPrincipal;
import com.brainserve.onboarding.common.util.CryptoSupport;
import com.brainserve.onboarding.identity.domain.model.UserAccount;
import com.brainserve.onboarding.identity.application.service.IdentityAccountService;
import com.brainserve.onboarding.organization.domain.model.Organization;
import com.brainserve.onboarding.organization.domain.model.OrganizationInvitation;
import com.brainserve.onboarding.organization.domain.model.OrganizationMembership;
import com.brainserve.onboarding.organization.application.service.OrganizationIdentityService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String REFRESH_COOKIE = "co_refresh";

    private final IdentityAccountService users;
    private final OrganizationIdentityService organizationIdentity;
    private final AuthSessionRepository sessions;
    private final ClientAuthSessionRepository clientSessions;
    private final SecurityTokenRepository securityTokens;
    private final PasswordEncoder passwordEncoder;
    private final String dummyPasswordHash;
    private final PasswordPolicy passwordPolicy;
    private final JwtEncoder jwtEncoder;
    private final SecurityProperties properties;
    private final AuthRateLimiter rateLimiter;
    private final LoginSecurityStateService loginSecurityState;
    private final SecurityMailService mailService;
    private final TotpService totpService;
    private final FieldEncryptionService encryption;
    private final AuditService audit;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public AuthService(IdentityAccountService users,
                       OrganizationIdentityService organizationIdentity,
                       AuthSessionRepository sessions,
                       ClientAuthSessionRepository clientSessions,
                       SecurityTokenRepository securityTokens,
                       PasswordEncoder passwordEncoder,
                       PasswordPolicy passwordPolicy,
                       JwtEncoder jwtEncoder,
                       SecurityProperties properties,
                       AuthRateLimiter rateLimiter,
                       LoginSecurityStateService loginSecurityState,
                       SecurityMailService mailService,
                       TotpService totpService,
                       FieldEncryptionService encryption,
                       AuditService audit,
                       Clock clock,
                       TransactionTemplate transactionTemplate) {
        this.users = users;
        this.organizationIdentity = organizationIdentity;
        this.sessions = sessions;
        this.clientSessions = clientSessions;
        this.securityTokens = securityTokens;
        this.passwordEncoder = passwordEncoder;
        this.dummyPasswordHash = passwordEncoder.encode(CryptoSupport.randomUrlToken(24));
        this.passwordPolicy = passwordPolicy;
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.loginSecurityState = loginSecurityState;
        this.mailService = mailService;
        this.totpService = totpService;
        this.encryption = encryption;
        this.audit = audit;
        this.clock = clock;
        this.transactionTemplate = transactionTemplate;
    }

    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletRequest servletRequest, HttpServletResponse response) {
        String normalizedEmail = UserAccount.normalizeEmail(request.email());
        String normalizedSlug = request.organizationSlug().trim().toLowerCase(Locale.ROOT);
        String ip = NetworkSource.clientIp(servletRequest);
        rateLimiter.consume("LOGIN_ACCOUNT", normalizedEmail + "|" + normalizedSlug);
        rateLimiter.consume("LOGIN_SOURCE", ip == null ? "unknown" : ip);

        Organization organization = organizationIdentity.findOrganizationBySlug(normalizedSlug).orElse(null);
        UserAccount user = users.findByNormalizedEmail(normalizedEmail).orElse(null);
        if (organization == null || user == null) {
            // Keep the password-verification path present for unknown identities/workspaces to reduce
            // account-enumeration timing differences.
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            throw invalidCredentials();
        }

        OrganizationMembership membership = organizationIdentity.findMembershipForUser(organization.getId(), user.getId()).orElse(null);
        Instant now = clock.instant();
        if (membership == null) {
            // A valid global identity that is not a member of this tenant must remain indistinguishable
            // from an unknown identity, and the foreign tenant must not be able to lock that identity.
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            throw invalidCredentials();
        }
        boolean passwordMatches = passwordEncoder.matches(request.password(), user.getPasswordHash());
        if (user.isLocked(now)) throw invalidCredentials();
        if (!passwordMatches) {
            loginSecurityState.recordFailedPassword(user.getId());
            throw invalidCredentials();
        }
        if (!"ACTIVE".equals(user.getStatus()) || !"ACTIVE".equals(membership.getStatus()) || !"ACTIVE".equals(organization.getStatus())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_INACTIVE", "This account is not active.");
        }
        if (user.getEmailVerifiedAt() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "EMAIL_VERIFICATION_REQUIRED", "Verify your email before signing in.");
        }

        user.recordSuccessfulLogin(now);
        users.save(user);
        Set<String> permissions = organizationIdentity.permissionCodes(organization.getId(), membership.getId());
        audit.record(organization.getId(), user.getId(), "AUTH_LOGIN_PASSWORD_ACCEPTED", "USER", user.getId(), null, null, servletRequest);
        return afterPasswordAuthentication(user, organization, membership, permissions, servletRequest, response);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        String rawRefresh = cookie(request, REFRESH_COOKIE);
        if (rawRefresh == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_SESSION", "Session is not valid.");
        String hash = CryptoSupport.sha256Hex(rawRefresh);
        AuthSession current = sessions.findByRefreshTokenHashForUpdate(hash)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_SESSION", "Session is not valid."));
        Instant now = clock.instant();
        if (current.getRevokedAt() != null) {
            sessions.revokeAllByUserId(current.getUserId(), "REFRESH_TOKEN_REUSE");
            clientSessions.revokeAllByUserId(current.getUserId(), "REFRESH_TOKEN_REUSE");
            clearRefreshCookie(response);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "SESSION_REUSE_DETECTED", "Session is no longer valid.");
        }
        if (!current.isActive(now)) {
            current.revoke("EXPIRED", now);
            clearRefreshCookie(response);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_SESSION", "Session is not valid.");
        }

        UserAccount user = users.findById(current.getUserId()).orElseThrow(() -> invalidSession());
        Organization organization = organizationIdentity.findOrganizationById(current.getOrganizationId()).orElseThrow(() -> invalidSession());
        OrganizationMembership membership = organizationIdentity.findMembership(current.getOrganizationId(), current.getOrganizationUserId())
                .orElseThrow(() -> invalidSession());
        if (user.getCredentialsVersion() != current.getCredentialsVersion()
                || !"ACTIVE".equals(user.getStatus()) || !"ACTIVE".equals(organization.getStatus())
                || !"ACTIVE".equals(membership.getStatus())) {
            current.revoke("SECURITY_STATE_CHANGED", now);
            clearRefreshCookie(response);
            throw invalidSession();
        }

        Set<String> permissions = organizationIdentity.permissionCodes(organization.getId(), membership.getId());
        if (!user.isMfaEnabled() && PrivilegedPermissionPolicy.requiresMfa(permissions)) {
            current.revoke("MFA_REQUIRED", now);
            clearRefreshCookie(response);
            TokenValue token = createSecurityToken(organization.getId(), user.getId(), "MFA_SETUP", properties.mfaChallengeTtl());
            return AuthResponse.mfaSetup(token.raw());
        }
        return rotateSession(current, user, organization, membership, permissions, request, response);
    }

    @Transactional
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String rawRefresh = cookie(request, REFRESH_COOKIE);
        if (rawRefresh != null) {
            sessions.findByRefreshTokenHashForUpdate(CryptoSupport.sha256Hex(rawRefresh))
                    .ifPresent(session -> session.revoke("LOGOUT", clock.instant()));
        }
        clearRefreshCookie(response);
    }

    public void forgotPassword(String email, String organizationSlug, HttpServletRequest request) {
        String normalizedEmail = UserAccount.normalizeEmail(email);
        String normalizedSlug = organizationSlug.trim().toLowerCase(Locale.ROOT);
        String ip = NetworkSource.clientIp(request);
        rateLimiter.consume("FORGOT_PASSWORD_ACCOUNT", normalizedEmail + "|" + normalizedSlug);
        rateLimiter.consume("FORGOT_PASSWORD_SOURCE", ip == null ? "unknown" : ip);

        SecurityMailDispatch dispatch = transactionTemplate.execute(status -> {
            Organization organization = organizationIdentity.findOrganizationBySlug(normalizedSlug).orElse(null);
            UserAccount user = users.findByNormalizedEmail(normalizedEmail).orElse(null);
            if (organization == null || user == null || !"ACTIVE".equals(organization.getStatus())
                    || !"ACTIVE".equals(user.getStatus())) return null;
            OrganizationMembership membership = organizationIdentity.findMembershipForUser(organization.getId(), user.getId()).orElse(null);
            if (membership == null || !"ACTIVE".equals(membership.getStatus())) return null;

            securityTokens.revokeOutstanding(user.getId(), "PASSWORD_RESET");
            TokenValue token = createSecurityToken(organization.getId(), user.getId(), "PASSWORD_RESET", properties.passwordResetTtl());
            audit.record(organization.getId(), user.getId(), "PASSWORD_RESET_REQUESTED", "USER", user.getId(), null, null, request);
            return new SecurityMailDispatch(user.getEmail(), token.raw(), "PASSWORD_RESET");
        });
        if (dispatch == null) return;
        try {
            mailService.sendPasswordReset(dispatch.email(), dispatch.rawToken());
        } catch (MailException ex) {
            revokeDeliveredSecurityToken(dispatch.rawToken(), dispatch.tokenType());
            log.error("Security email delivery failed for password reset request", ex);
        }
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword, HttpServletRequest request) {
        SecurityToken token = requireSecurityToken(rawToken, "PASSWORD_RESET");
        UserAccount user = users.findById(token.getUserId()).orElseThrow(() -> invalidToken());
        passwordPolicy.validate(newPassword, user.getEmail());
        Instant now = clock.instant();
        user.changePassword(passwordEncoder.encode(newPassword), now);
        users.save(user);
        token.consume(now);
        sessions.revokeAllByUserId(user.getId(), "PASSWORD_RESET");
        clientSessions.revokeAllByUserId(user.getId(), "PASSWORD_RESET");
        audit.record(token.getOrganizationId(), user.getId(), "PASSWORD_RESET_COMPLETED", "USER", user.getId(), null, null, request);
    }

    @Transactional
    public void verifyEmail(String rawToken, HttpServletRequest request) {
        SecurityToken token = requireSecurityToken(rawToken, "EMAIL_VERIFICATION");
        UserAccount user = users.findById(token.getUserId()).orElseThrow(() -> invalidToken());
        Instant now = clock.instant();
        user.markEmailVerified(now);
        users.save(user);
        token.consume(now);
        audit.record(token.getOrganizationId(), user.getId(), "EMAIL_VERIFIED", "USER", user.getId(), null, null, request);
    }

    public void resendVerification(String email, String organizationSlug, HttpServletRequest request) {
        String normalizedEmail = UserAccount.normalizeEmail(email);
        String normalizedSlug = organizationSlug.trim().toLowerCase(Locale.ROOT);
        String ip = NetworkSource.clientIp(request);
        rateLimiter.consume("VERIFY_EMAIL_ACCOUNT", normalizedEmail + "|" + normalizedSlug);
        rateLimiter.consume("VERIFY_EMAIL_SOURCE", ip == null ? "unknown" : ip);

        SecurityMailDispatch dispatch = transactionTemplate.execute(status -> {
            Organization organization = organizationIdentity.findOrganizationBySlug(normalizedSlug).orElse(null);
            UserAccount user = users.findByNormalizedEmail(normalizedEmail).orElse(null);
            if (organization == null || user == null || user.getEmailVerifiedAt() != null
                    || !"ACTIVE".equals(organization.getStatus()) || !"ACTIVE".equals(user.getStatus())) return null;
            OrganizationMembership membership = organizationIdentity.findMembershipForUser(organization.getId(), user.getId()).orElse(null);
            if (membership == null || !"ACTIVE".equals(membership.getStatus())) return null;

            securityTokens.revokeOutstanding(user.getId(), "EMAIL_VERIFICATION");
            TokenValue token = createSecurityToken(organization.getId(), user.getId(), "EMAIL_VERIFICATION", properties.emailVerificationTtl());
            return new SecurityMailDispatch(user.getEmail(), token.raw(), "EMAIL_VERIFICATION");
        });
        if (dispatch == null) return;
        try {
            mailService.sendEmailVerification(dispatch.email(), dispatch.rawToken());
        } catch (MailException ex) {
            revokeDeliveredSecurityToken(dispatch.rawToken(), dispatch.tokenType());
            log.error("Security email delivery failed for email verification request", ex);
        }
    }

    @Transactional
    public MfaSetupResponse beginMfaSetup(String setupToken, HttpServletRequest request) {
        String ip = NetworkSource.clientIp(request);
        rateLimiter.consume("MFA_SETUP_START", CryptoSupport.sha256Hex(setupToken) + "|" + (ip == null ? "unknown" : ip));
        SecurityToken token = requireSecurityToken(setupToken, "MFA_SETUP");
        UserAccount user = users.findById(token.getUserId()).orElseThrow(() -> invalidToken());
        Organization org = organizationIdentity.findOrganizationById(token.getOrganizationId()).orElseThrow(() -> invalidToken());
        OrganizationMembership membership = organizationIdentity.findMembershipForUser(org.getId(), user.getId())
                .orElseThrow(() -> invalidToken());
        requireActiveContext(user, org, membership);
        String secret = totpService.generateSecret();
        String context = "mfa:" + user.getId();
        user.stageMfaSecret(encryption.encrypt(secret, context), clock.instant());
        users.save(user);
        String label = URLEncoder.encode(org.getName() + ":" + user.getEmail(), StandardCharsets.UTF_8).replace("+", "%20");
        String issuer = URLEncoder.encode(org.getName(), StandardCharsets.UTF_8).replace("+", "%20");
        String uri = "otpauth://totp/" + label + "?secret=" + secret + "&issuer=" + issuer + "&digits=6&period=30";
        return new MfaSetupResponse(secret, uri);
    }

    @Transactional
    public AuthResponse confirmMfaSetup(String setupToken, String code, HttpServletRequest request, HttpServletResponse response) {
        String ip = NetworkSource.clientIp(request);
        rateLimiter.consume("MFA_SETUP", CryptoSupport.sha256Hex(setupToken) + "|" + (ip == null ? "unknown" : ip));
        SecurityToken token = requireSecurityToken(setupToken, "MFA_SETUP");
        UserAccount user = users.findById(token.getUserId()).orElseThrow(() -> invalidToken());
        Organization organization = organizationIdentity.findOrganizationById(token.getOrganizationId()).orElseThrow(() -> invalidToken());
        OrganizationMembership membership = organizationIdentity.findMembershipForUser(organization.getId(), user.getId())
                .orElseThrow(() -> invalidToken());
        requireActiveContext(user, organization, membership);
        if (user.getMfaPendingSecretEncrypted() == null) throw invalidToken();
        String secret = encryption.decrypt(user.getMfaPendingSecretEncrypted(), "mfa:" + user.getId());
        if (!totpService.verify(secret, code)) throw new ApiException(HttpStatus.UNAUTHORIZED, "MFA_CODE_INVALID", "The verification code is invalid.");
        Instant now = clock.instant();
        user.enableMfaFromPending(now);
        users.save(user);
        sessions.revokeAllByUserId(user.getId(), "MFA_ENABLED");
        clientSessions.revokeAllByUserId(user.getId(), "MFA_ENABLED");
        token.consume(now);
        Set<String> permissions = organizationIdentity.permissionCodes(organization.getId(), membership.getId());
        audit.record(organization.getId(), user.getId(), "MFA_ENABLED", "USER", user.getId(), null, null, request);
        return issueSession(user, organization, membership, permissions, request, response);
    }

    @Transactional
    public AuthResponse verifyMfa(String challengeToken, String code, HttpServletRequest request, HttpServletResponse response) {
        String ip = NetworkSource.clientIp(request);
        rateLimiter.consume("MFA_VERIFY", CryptoSupport.sha256Hex(challengeToken) + "|" + (ip == null ? "unknown" : ip));
        SecurityToken token = requireSecurityToken(challengeToken, "MFA_LOGIN");
        UserAccount user = users.findById(token.getUserId()).orElseThrow(() -> invalidToken());
        Organization organization = organizationIdentity.findOrganizationById(token.getOrganizationId()).orElseThrow(() -> invalidToken());
        OrganizationMembership membership = organizationIdentity.findMembershipForUser(organization.getId(), user.getId())
                .orElseThrow(() -> invalidToken());
        requireActiveContext(user, organization, membership);
        if (!user.isMfaEnabled() || user.getMfaSecretEncrypted() == null) throw invalidToken();
        String secret = encryption.decrypt(user.getMfaSecretEncrypted(), "mfa:" + user.getId());
        if (!totpService.verify(secret, code)) throw new ApiException(HttpStatus.UNAUTHORIZED, "MFA_CODE_INVALID", "The verification code is invalid.");
        token.consume(clock.instant());
        Set<String> permissions = organizationIdentity.permissionCodes(organization.getId(), membership.getId());
        audit.record(organization.getId(), user.getId(), "AUTH_MFA_ACCEPTED", "USER", user.getId(), null, null, request);
        return issueSession(user, organization, membership, permissions, request, response);
    }

    @Transactional
    public AuthResponse acceptInvitation(AcceptInvitationRequest request, HttpServletRequest servletRequest, HttpServletResponse response) {
        String hash = CryptoSupport.sha256Hex(request.token());
        String ip = NetworkSource.clientIp(servletRequest);
        rateLimiter.consume("INVITATION_ACCEPT", hash + "|" + (ip == null ? "unknown" : ip));
        OrganizationInvitation invitation = organizationIdentity.findInvitationByTokenHashForUpdate(hash).orElseThrow(() -> invalidToken());
        Instant now = clock.instant();
        if (!invitation.isUsable(now)) throw invalidToken();
        Organization organization = organizationIdentity.findOrganizationById(invitation.getOrganizationId()).orElseThrow(() -> invalidToken());
        requireActiveOrganization(organization);

        users.lockNormalizedEmail(invitation.getNormalizedEmail());
        UserAccount user = users.findByNormalizedEmail(invitation.getNormalizedEmail()).orElse(null);
        if (user == null) {
            passwordPolicy.validate(request.password(), invitation.getEmail());
            user = new UserAccount(UUID.randomUUID(), invitation.getEmail(), invitation.getDisplayName(),
                    passwordEncoder.encode(request.password()), now, true);
            users.saveAndFlush(user);
        } else {
            if (user.isLocked(now)) throw invalidCredentials();
            if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
                loginSecurityState.recordFailedPassword(user.getId());
                throw new ApiException(HttpStatus.UNAUTHORIZED, "INVITATION_CREDENTIALS_REQUIRED",
                        "Use the password for your existing account to accept this invitation.");
            }
            user.recordSuccessfulLogin(now);
            if (user.getEmailVerifiedAt() == null) user.markEmailVerified(now);
            users.saveAndFlush(user);
        }

        OrganizationMembership membership = organizationIdentity.findMembershipForUser(organization.getId(), user.getId())
                .orElseGet(() -> organizationIdentity.saveMembership(new OrganizationMembership(
                        UUID.randomUUID(), organization.getId(), user.getId(), invitation.getInvitedBy(), now)));
        organizationIdentity.flushMemberships();
        List<UUID> roleIds = organizationIdentity.invitationRoleIds(organization.getId(), invitation.getId());
        organizationIdentity.replaceMembershipRoles(organization.getId(), membership.getId(), Set.copyOf(roleIds), invitation.getInvitedBy());
        invitation.accept(user.getId(), now);
        organizationIdentity.saveInvitation(invitation);
        Set<String> permissions = organizationIdentity.permissionCodes(organization.getId(), membership.getId());
        audit.record(organization.getId(), user.getId(), "ORGANIZATION_INVITATION_ACCEPTED", "ORGANIZATION_USER", membership.getId(), null, null, servletRequest);
        return afterPasswordAuthentication(user, organization, membership, permissions, servletRequest, response);
    }

    @Transactional
    public void changePassword(TenantPrincipal principal, ChangePasswordRequest request,
                               HttpServletRequest servletRequest, HttpServletResponse response) {
        UserAccount user = users.findById(principal.userId()).orElseThrow(() -> invalidSession());
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) throw invalidCredentials();
        passwordPolicy.validate(request.newPassword(), user.getEmail());
        user.changePassword(passwordEncoder.encode(request.newPassword()), clock.instant());
        users.save(user);
        sessions.revokeAllByUserId(user.getId(), "PASSWORD_CHANGED");
        clientSessions.revokeAllByUserId(user.getId(), "PASSWORD_CHANGED");
        clearRefreshCookie(response);
        audit.record(principal.organizationId(), user.getId(), "PASSWORD_CHANGED", "USER", user.getId(), null, null, servletRequest);
    }

    public AuthUserResponse me(TenantPrincipal principal) {
        return new AuthUserResponse(principal.userId(), principal.email(), principal.displayName(), principal.organizationId(),
                principal.organizationName(), principal.organizationSlug(), principal.permissions());
    }

    private AuthResponse afterPasswordAuthentication(UserAccount user, Organization organization,
                                                     OrganizationMembership membership, Set<String> permissions,
                                                     HttpServletRequest request, HttpServletResponse response) {
        requireActiveContext(user, organization, membership);
        if (user.getEmailVerifiedAt() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "EMAIL_VERIFICATION_REQUIRED", "Verify your email before signing in.");
        }
        if (user.isMfaEnabled()) {
            TokenValue token = createSecurityToken(organization.getId(), user.getId(), "MFA_LOGIN", properties.mfaChallengeTtl());
            return AuthResponse.mfaChallenge(token.raw());
        }
        if (PrivilegedPermissionPolicy.requiresMfa(permissions)) {
            TokenValue token = createSecurityToken(organization.getId(), user.getId(), "MFA_SETUP", properties.mfaChallengeTtl());
            return AuthResponse.mfaSetup(token.raw());
        }
        return issueSession(user, organization, membership, permissions, request, response);
    }

    private AuthResponse rotateSession(AuthSession current, UserAccount user, Organization organization,
                                       OrganizationMembership membership, Set<String> permissions,
                                       HttpServletRequest request, HttpServletResponse response) {
        String rawRefresh = CryptoSupport.randomUrlToken(48);
        Instant now = clock.instant();
        AuthSession replacement = new AuthSession(UUID.randomUUID(), organization.getId(), user.getId(), membership.getId(),
                CryptoSupport.sha256Hex(rawRefresh), user.getCredentialsVersion(), now.plus(properties.refreshTokenTtl()),
                NetworkSource.clientIp(request), userAgentHash(request), now);
        sessions.save(replacement);
        current.rotateTo(replacement.getId(), now);
        setRefreshCookie(response, rawRefresh);
        return createAuthResponse(user, organization, membership, permissions, replacement);
    }

    private AuthResponse issueSession(UserAccount user, Organization organization, OrganizationMembership membership,
                                      Set<String> permissions, HttpServletRequest request, HttpServletResponse response) {
        String rawRefresh = CryptoSupport.randomUrlToken(48);
        Instant now = clock.instant();
        AuthSession session = new AuthSession(UUID.randomUUID(), organization.getId(), user.getId(), membership.getId(),
                CryptoSupport.sha256Hex(rawRefresh), user.getCredentialsVersion(), now.plus(properties.refreshTokenTtl()),
                NetworkSource.clientIp(request), userAgentHash(request), now);
        sessions.save(session);
        setRefreshCookie(response, rawRefresh);
        return createAuthResponse(user, organization, membership, permissions, session);
    }

    private AuthResponse createAuthResponse(UserAccount user, Organization organization, OrganizationMembership membership,
                                            Set<String> permissions, AuthSession session) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.tokenIssuer())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(user.getId().toString())
                .audience(List.of(properties.tokenAudience()))
                .claim("org", organization.getId().toString())
                .claim("mid", membership.getId().toString())
                .claim("sid", session.getId().toString())
                .claim("cv", user.getCredentialsVersion())
                .build();
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        AuthUserResponse authUser = new AuthUserResponse(user.getId(), user.getEmail(), user.getDisplayName(),
                organization.getId(), organization.getName(), organization.getSlug(), permissions);
        return AuthResponse.authenticated(accessToken, expiresAt, authUser);
    }

    private TokenValue createSecurityToken(UUID organizationId, UUID userId, String type, java.time.Duration ttl) {
        String raw = CryptoSupport.randomUrlToken(32);
        SecurityToken entity = new SecurityToken(UUID.randomUUID(), organizationId, userId, type,
                CryptoSupport.sha256Hex(raw), clock.instant().plus(ttl), clock.instant());
        securityTokens.save(entity);
        return new TokenValue(raw, entity);
    }

    private SecurityToken requireSecurityToken(String raw, String type) {
        SecurityToken token = securityTokens.findByTokenHashAndTokenTypeForUpdate(CryptoSupport.sha256Hex(raw), type)
                .orElseThrow(() -> invalidToken());
        if (!token.isUsable(clock.instant())) throw invalidToken();
        return token;
    }

    private void revokeDeliveredSecurityToken(String rawToken, String tokenType) {
        transactionTemplate.executeWithoutResult(status -> securityTokens
                .findByTokenHashAndTokenTypeForUpdate(CryptoSupport.sha256Hex(rawToken), tokenType)
                .ifPresent(token -> token.revoke(clock.instant())));
    }

    private void setRefreshCookie(HttpServletResponse response, String rawRefresh) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, rawRefresh)
                .httpOnly(true)
                .secure(properties.secureCookies())
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(properties.refreshTokenTtl())
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(properties.secureCookies())
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private static String cookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) if (name.equals(cookie.getName())) return cookie.getValue();
        return null;
    }

    private static String userAgentHash(HttpServletRequest request) {
        String value = request.getHeader("User-Agent");
        return value == null ? null : CryptoSupport.sha256Hex(value);
    }


    private static void requireActiveOrganization(Organization organization) {
        if (!"ACTIVE".equals(organization.getStatus())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_INACTIVE", "This account is not active.");
        }
    }

    private static void requireActiveContext(UserAccount user, Organization organization, OrganizationMembership membership) {
        if (!"ACTIVE".equals(user.getStatus()) || !"ACTIVE".equals(organization.getStatus()) || !"ACTIVE".equals(membership.getStatus())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_INACTIVE", "This account is not active.");
        }
    }

    private static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email, workspace, or password is incorrect.");
    }

    private static ApiException invalidSession() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_SESSION", "Session is not valid.");
    }

    private static ApiException invalidToken() {
        return new ApiException(HttpStatus.BAD_REQUEST, "SECURITY_TOKEN_INVALID", "This security link is invalid or expired.");
    }

    private record TokenValue(String raw, SecurityToken entity) {}
    private record SecurityMailDispatch(String email, String rawToken, String tokenType) {}
}
