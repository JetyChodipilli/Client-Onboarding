package com.brainserve.onboarding.auth.application.service;

import com.brainserve.onboarding.audit.application.service.AuditService;
import com.brainserve.onboarding.auth.api.request.ClientLoginRequest;
import com.brainserve.onboarding.auth.api.response.AuthResponse;
import com.brainserve.onboarding.auth.api.response.AuthUserResponse;
import com.brainserve.onboarding.auth.domain.model.ClientAuthSession;
import com.brainserve.onboarding.auth.domain.model.ClientSecurityToken;
import com.brainserve.onboarding.auth.infrastructure.persistence.AuthSessionRepository;
import com.brainserve.onboarding.auth.infrastructure.persistence.ClientAuthSessionRepository;
import com.brainserve.onboarding.auth.infrastructure.persistence.ClientSecurityTokenRepository;
import com.brainserve.onboarding.client.application.service.ClientPortalAccessService;
import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.common.security.ClientPrincipal;
import com.brainserve.onboarding.common.security.FieldEncryptionService;
import com.brainserve.onboarding.common.security.NetworkSource;
import com.brainserve.onboarding.common.security.SecurityProperties;
import com.brainserve.onboarding.common.util.CryptoSupport;
import com.brainserve.onboarding.identity.application.service.IdentityAccountService;
import com.brainserve.onboarding.identity.domain.model.UserAccount;
import com.brainserve.onboarding.organization.application.service.OrganizationIdentityService;
import com.brainserve.onboarding.organization.domain.model.Organization;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
public class ClientAuthService {
    private static final Logger log = LoggerFactory.getLogger(ClientAuthService.class);
    private static final String REFRESH_COOKIE = "co_client_refresh";

    private final IdentityAccountService users;
    private final OrganizationIdentityService organizations;
    private final ClientPortalAccessService portalAccess;
    private final ClientAuthSessionRepository sessions;
    private final AuthSessionRepository internalSessions;
    private final ClientSecurityTokenRepository securityTokens;
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

    public ClientAuthService(IdentityAccountService users,
                             OrganizationIdentityService organizations,
                             ClientPortalAccessService portalAccess,
                             ClientAuthSessionRepository sessions,
                             AuthSessionRepository internalSessions,
                             ClientSecurityTokenRepository securityTokens,
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
        this.organizations = organizations;
        this.portalAccess = portalAccess;
        this.sessions = sessions;
        this.internalSessions = internalSessions;
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
    public AuthResponse login(ClientLoginRequest request, HttpServletRequest servletRequest, HttpServletResponse response) {
        String email = UserAccount.normalizeEmail(request.email());
        String slug = request.organizationSlug().trim().toLowerCase(Locale.ROOT);
        String ip = NetworkSource.clientIp(servletRequest);
        rateLimiter.consume("CLIENT_LOGIN_ACCOUNT", email + "|" + slug);
        rateLimiter.consume("CLIENT_LOGIN_SOURCE", ip == null ? "unknown" : ip);

        Organization organization = organizations.findOrganizationBySlug(slug).orElse(null);
        UserAccount user = users.findByNormalizedEmail(email).orElse(null);
        if (organization == null || user == null || !portalAccess.hasActiveTenantAccess(organization.getId(), user.getId())) {
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            throw invalidCredentials();
        }
        Instant now = clock.instant();
        boolean matches = passwordEncoder.matches(request.password(), user.getPasswordHash());
        if (user.isLocked(now)) throw invalidCredentials();
        if (!matches) {
            loginSecurityState.recordFailedPassword(user.getId());
            throw invalidCredentials();
        }
        requireActive(user, organization);
        if (user.getEmailVerifiedAt() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "EMAIL_VERIFICATION_REQUIRED", "Verify your email before signing in.");
        }
        user.recordSuccessfulLogin(now);
        users.save(user);
        audit.recordClient(organization.getId(), user.getId(), "CLIENT_AUTH_LOGIN_PASSWORD_ACCEPTED", "USER", user.getId(), null, null, servletRequest);
        return afterPasswordAuthentication(user, organization, servletRequest, response);
    }

    /** Called only after the invitation transaction has created/validated an ACTIVE client-user relationship. */
    @Transactional
    public AuthResponse authenticateAfterInvitation(UUID organizationId, UserAccount user,
                                                    HttpServletRequest request, HttpServletResponse response) {
        Organization organization = organizations.findOrganizationById(organizationId).orElseThrow(ClientAuthService::invalidSession);
        requireActive(user, organization);
        if (!portalAccess.hasActiveTenantAccess(organizationId, user.getId())) throw invalidSession();
        if (user.isMfaEnabled()) {
            return AuthResponse.mfaChallenge(createSecurityToken(organizationId, user.getId(), "MFA_LOGIN", properties.mfaChallengeTtl()));
        }
        return issueSession(user, organization, request, response);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        String raw = cookie(request, REFRESH_COOKIE);
        if (raw == null) throw invalidSession();
        ClientAuthSession current = sessions.findByRefreshTokenHashForUpdate(CryptoSupport.sha256Hex(raw)).orElseThrow(ClientAuthService::invalidSession);
        Instant now = clock.instant();
        if (current.getRevokedAt() != null) {
            sessions.revokeAllByUserId(current.getUserId(), "REFRESH_TOKEN_REUSE");
            internalSessions.revokeAllByUserId(current.getUserId(), "REFRESH_TOKEN_REUSE");
            clearRefreshCookie(response);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "SESSION_REUSE_DETECTED", "Session is no longer valid.");
        }
        if (!current.isActive(now)) {
            current.revoke("EXPIRED", now);
            clearRefreshCookie(response);
            throw invalidSession();
        }
        UserAccount user = users.findById(current.getUserId()).orElseThrow(ClientAuthService::invalidSession);
        Organization organization = organizations.findOrganizationById(current.getOrganizationId()).orElseThrow(ClientAuthService::invalidSession);
        if (user.getCredentialsVersion() != current.getCredentialsVersion()
                || !portalAccess.hasActiveTenantAccess(organization.getId(), user.getId())) {
            current.revoke("SECURITY_STATE_CHANGED", now);
            clearRefreshCookie(response);
            throw invalidSession();
        }
        requireActive(user, organization);
        return rotateSession(current, user, organization, request, response);
    }

    @Transactional
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String raw = cookie(request, REFRESH_COOKIE);
        if (raw != null) sessions.findByRefreshTokenHashForUpdate(CryptoSupport.sha256Hex(raw))
                .ifPresent(value -> value.revoke("LOGOUT", clock.instant()));
        clearRefreshCookie(response);
    }

    public void forgotPassword(String emailValue, String organizationSlug, HttpServletRequest request) {
        String email = UserAccount.normalizeEmail(emailValue);
        String slug = organizationSlug.trim().toLowerCase(Locale.ROOT);
        String ip = NetworkSource.clientIp(request);
        rateLimiter.consume("CLIENT_FORGOT_PASSWORD_ACCOUNT", email + "|" + slug);
        rateLimiter.consume("CLIENT_FORGOT_PASSWORD_SOURCE", ip == null ? "unknown" : ip);
        MailDispatch dispatch = transactionTemplate.execute(status -> {
            Organization organization = organizations.findOrganizationBySlug(slug).orElse(null);
            UserAccount user = users.findByNormalizedEmail(email).orElse(null);
            if (organization == null || user == null || !"ACTIVE".equals(organization.getStatus())
                    || !"ACTIVE".equals(user.getStatus()) || !portalAccess.hasActiveTenantAccess(organization.getId(), user.getId())) return null;
            securityTokens.revokeOutstanding(organization.getId(), user.getId(), "PASSWORD_RESET");
            String token = createSecurityToken(organization.getId(), user.getId(), "PASSWORD_RESET", properties.passwordResetTtl());
            audit.recordClient(organization.getId(), user.getId(), "CLIENT_PASSWORD_RESET_REQUESTED", "USER", user.getId(), null, null, request);
            return new MailDispatch(user.getEmail(), token, organization.getId(), user.getId());
        });
        if (dispatch == null) return;
        try { mailService.sendClientPasswordReset(dispatch.email(), dispatch.rawToken()); }
        catch (MailException ex) {
            transactionTemplate.executeWithoutResult(status -> securityTokens
                    .findByTokenHashForUpdate(CryptoSupport.sha256Hex(dispatch.rawToken()))
                    .ifPresent(token -> token.revoke(clock.instant())));
            log.error("Client password-reset email delivery failed", ex);
        }
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword, HttpServletRequest request) {
        ClientSecurityToken token = requireToken(rawToken, "PASSWORD_RESET");
        UserAccount user = users.findById(token.getUserId()).orElseThrow(ClientAuthService::invalidToken);
        if (!portalAccess.hasActiveTenantAccess(token.getOrganizationId(), user.getId())) throw invalidToken();
        passwordPolicy.validate(newPassword, user.getEmail());
        Instant now = clock.instant();
        user.changePassword(passwordEncoder.encode(newPassword), now);
        users.save(user);
        token.consume(now);
        sessions.revokeAllByUserId(user.getId(), "PASSWORD_RESET");
        internalSessions.revokeAllByUserId(user.getId(), "PASSWORD_RESET");
        audit.recordClient(token.getOrganizationId(), user.getId(), "CLIENT_PASSWORD_RESET_COMPLETED", "USER", user.getId(), null, null, request);
    }

    @Transactional
    public AuthResponse verifyMfa(String challengeToken, String code, HttpServletRequest request, HttpServletResponse response) {
        String ip = NetworkSource.clientIp(request);
        rateLimiter.consume("CLIENT_MFA_VERIFY", CryptoSupport.sha256Hex(challengeToken) + "|" + (ip == null ? "unknown" : ip));
        ClientSecurityToken token = requireToken(challengeToken, "MFA_LOGIN");
        UserAccount user = users.findById(token.getUserId()).orElseThrow(ClientAuthService::invalidToken);
        Organization organization = organizations.findOrganizationById(token.getOrganizationId()).orElseThrow(ClientAuthService::invalidToken);
        requireActive(user, organization);
        if (!portalAccess.hasActiveTenantAccess(organization.getId(), user.getId())
                || !user.isMfaEnabled() || user.getMfaSecretEncrypted() == null) throw invalidToken();
        String secret = encryption.decrypt(user.getMfaSecretEncrypted(), "mfa:" + user.getId());
        if (!totpService.verify(secret, code)) throw new ApiException(HttpStatus.UNAUTHORIZED, "MFA_CODE_INVALID", "The verification code is invalid.");
        token.consume(clock.instant());
        audit.recordClient(organization.getId(), user.getId(), "CLIENT_AUTH_MFA_ACCEPTED", "USER", user.getId(), null, null, request);
        return issueSession(user, organization, request, response);
    }

    public AuthUserResponse me(ClientPrincipal principal) {
        return new AuthUserResponse(principal.userId(), principal.email(), principal.displayName(), principal.organizationId(),
                principal.organizationName(), principal.organizationSlug(), principal.permissions());
    }

    private AuthResponse afterPasswordAuthentication(UserAccount user, Organization organization,
                                                     HttpServletRequest request, HttpServletResponse response) {
        if (user.isMfaEnabled()) return AuthResponse.mfaChallenge(
                createSecurityToken(organization.getId(), user.getId(), "MFA_LOGIN", properties.mfaChallengeTtl()));
        return issueSession(user, organization, request, response);
    }

    private String createSecurityToken(UUID organizationId, UUID userId, String type, java.time.Duration ttl) {
        String raw = CryptoSupport.randomUrlToken(32);
        securityTokens.save(new ClientSecurityToken(UUID.randomUUID(), organizationId, userId, type,
                CryptoSupport.sha256Hex(raw), clock.instant().plus(ttl), clock.instant()));
        return raw;
    }

    private ClientSecurityToken requireToken(String raw, String type) {
        ClientSecurityToken token = securityTokens.findByTokenHashForUpdate(CryptoSupport.sha256Hex(raw)).orElseThrow(ClientAuthService::invalidToken);
        if (!type.equals(token.getTokenType()) || !token.usable(clock.instant())) throw invalidToken();
        return token;
    }

    private AuthResponse issueSession(UserAccount user, Organization organization,
                                      HttpServletRequest request, HttpServletResponse response) {
        String rawRefresh = CryptoSupport.randomUrlToken(48);
        Instant now = clock.instant();
        ClientAuthSession session = sessions.save(new ClientAuthSession(UUID.randomUUID(), organization.getId(), user.getId(),
                CryptoSupport.sha256Hex(rawRefresh), user.getCredentialsVersion(), now.plus(properties.refreshTokenTtl()),
                NetworkSource.clientIp(request), userAgentHash(request), now));
        setRefreshCookie(response, rawRefresh);
        return createAuthResponse(user, organization, session);
    }

    private AuthResponse rotateSession(ClientAuthSession current, UserAccount user, Organization organization,
                                       HttpServletRequest request, HttpServletResponse response) {
        String rawRefresh = CryptoSupport.randomUrlToken(48);
        Instant now = clock.instant();
        ClientAuthSession replacement = sessions.save(new ClientAuthSession(UUID.randomUUID(), organization.getId(), user.getId(),
                CryptoSupport.sha256Hex(rawRefresh), user.getCredentialsVersion(), now.plus(properties.refreshTokenTtl()),
                NetworkSource.clientIp(request), userAgentHash(request), now));
        current.rotateTo(replacement.getId(), now);
        setRefreshCookie(response, rawRefresh);
        return createAuthResponse(user, organization, replacement);
    }

    private AuthResponse createAuthResponse(UserAccount user, Organization organization, ClientAuthSession session) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(properties.tokenIssuer()).issuedAt(now).expiresAt(expiresAt)
                .subject(user.getId().toString()).audience(List.of(properties.tokenAudience()))
                .claim("pt", "CLIENT").claim("org", organization.getId().toString())
                .claim("sid", session.getId().toString()).claim("cv", user.getCredentialsVersion()).build();
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        return AuthResponse.authenticated(accessToken, expiresAt, new AuthUserResponse(user.getId(), user.getEmail(), user.getDisplayName(),
                organization.getId(), organization.getName(), organization.getSlug(), Set.of("CLIENT_PORTAL")));
    }

    private void requireActive(UserAccount user, Organization organization) {
        if (!"ACTIVE".equals(user.getStatus()) || !"ACTIVE".equals(organization.getStatus())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_INACTIVE", "This client portal account is not active.");
        }
    }

    private void setRefreshCookie(HttpServletResponse response, String raw) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, raw).httpOnly(true).secure(properties.secureCookies())
                .sameSite("Strict").path("/api/v1/client-auth").maxAge(properties.refreshTokenTtl()).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, "").httpOnly(true).secure(properties.secureCookies())
                .sameSite("Strict").path("/api/v1/client-auth").maxAge(0).build();
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
        return value == null ? null : CryptoSupport.sha256Hex(value.substring(0, Math.min(value.length(), 1024)));
    }

    private static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email, workspace, or password is incorrect.");
    }
    private static ApiException invalidSession() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_SESSION", "Session is not valid.");
    }
    private static ApiException invalidToken() {
        return new ApiException(HttpStatus.BAD_REQUEST, "TOKEN_INVALID", "This security link is invalid or expired.");
    }

    private record MailDispatch(String email, String rawToken, UUID organizationId, UUID userId) {}
}
