package com.brainserve.clientonboarding.auth.application;

import com.brainserve.clientonboarding.audit.application.AuditService;
import com.brainserve.clientonboarding.common.observability.RequestMetadata;
import com.brainserve.clientonboarding.auth.domain.model.AuthChallenge;
import com.brainserve.clientonboarding.auth.domain.model.AuthSession;
import com.brainserve.clientonboarding.common.security.TenantPrincipal;
import com.brainserve.clientonboarding.auth.domain.repository.AuthRepository;
import com.brainserve.clientonboarding.auth.infrastructure.configuration.AuthProperties;
import com.brainserve.clientonboarding.common.error.DomainException;
import com.brainserve.clientonboarding.identity.domain.repository.IdentityRepository;
import com.brainserve.clientonboarding.organization.domain.model.OrganizationAccess;
import com.brainserve.clientonboarding.organization.domain.repository.OrganizationAccessRepository;
import com.brainserve.clientonboarding.organization.domain.repository.OrganizationAdminRepository;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthService.class);
    private final OrganizationAccessRepository accessRepository;
    private final ClientSessionAccessPort clientAccess;
    private final OrganizationAdminRepository organizationRepository;
    private final IdentityRepository identityRepository;
    private final AuthRepository authRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final SecureTokenService tokens;
    private final TotpService totp;
    private final MfaCipher cipher;
    private final SecurityNotificationPort notifications;
    private final LoginRateLimiter rateLimiter;
    private final AuditService audit;
    private final AuthProperties properties;
    private final Clock clock;
    private final String dummyHash;

    public AuthService(OrganizationAccessRepository accessRepository,
                       ClientSessionAccessPort clientAccess,
                       OrganizationAdminRepository organizationRepository,
                       IdentityRepository identityRepository,
                       AuthRepository authRepository,
                       PasswordEncoder passwordEncoder,
                       PasswordPolicy passwordPolicy,
                       SecureTokenService tokens,
                       TotpService totp,
                       MfaCipher cipher,
                       SecurityNotificationPort notifications,
                       LoginRateLimiter rateLimiter,
                       AuditService audit,
                       AuthProperties properties,
                       Clock clock) {
        this.accessRepository = accessRepository;
        this.clientAccess = clientAccess;
        this.organizationRepository = organizationRepository;
        this.identityRepository = identityRepository;
        this.authRepository = authRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.tokens = tokens;
        this.totp = totp;
        this.cipher = cipher;
        this.notifications = notifications;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode(tokens.issue());
    }

    @Transactional(noRollbackFor = DomainException.class)
    public LoginOutcome login(LoginCommand command, RequestMetadata metadata) {
        Instant now = clock.instant();
        String email = normalizeEmail(command.email());
        String slug = normalizeSlug(command.organizationSlug());
        String subjectKey = tokens.hash(metadata.ipHash() + "|" + email + "|" + slug);
        rateLimiter.check(metadata.ipHash(), subjectKey);
        var access = accessRepository.findByEmailAndSlug(email, slug).orElse(null);
        String candidateHash = access == null ? dummyHash : access.user().passwordHash();
        boolean passwordMatches = passwordEncoder.matches(command.password(), candidateHash);

        if (access == null || !passwordMatches || !access.isUsableInternalAccess()
                || !access.user().isActiveAt(now)) {
            if (access != null && !passwordMatches && access.isUsableInternalAccess()
                    && (access.user().lockedUntil() == null || !access.user().lockedUntil().isAfter(now))) {
                failedLogin(access, now, metadata.ipHash());
            }
            rateLimiter.failure(metadata.ipHash(), subjectKey);
            throw unauthorized();
        }

        identityRepository.clearFailedLogins(access.user().id(), now);
        rateLimiter.success(subjectKey);
        var mfaMethod = authRepository.findMfaMethod(access.user().id());
        boolean privileged = MfaAssurance.requiredFor(access.permissions());
        if (mfaMethod.isPresent() || privileged) {
            String challengeToken = tokens.issue();
            AuthChallenge.Purpose purpose;
            String encryptedSecret = null;
            String enrollmentSecret = null;
            String otpAuthUri = null;
            if (mfaMethod.isPresent()) {
                purpose = AuthChallenge.Purpose.MFA_VERIFY;
            } else {
                purpose = AuthChallenge.Purpose.MFA_ENROLL;
                enrollmentSecret = totp.newSecret();
                encryptedSecret = cipher.encrypt(enrollmentSecret);
                otpAuthUri = otpAuthUri(access, enrollmentSecret);
            }
            authRepository.insertChallenge(new AuthChallenge(UUID.randomUUID(), access.organization().id(),
                    access.user().id(), tokens.hash(challengeToken), purpose, encryptedSecret, 0,
                    now.plus(properties.tokenDuration()), null), now);
            audit.append(access.organization().id(), access.user().id(), "AUTH_PASSWORD_ACCEPTED",
                    "USER", access.user().id(), Map.of(), Map.of("mfaRequired", true), "API", metadata.ipHash());
            return new LoginOutcome(purpose == AuthChallenge.Purpose.MFA_ENROLL
                    ? LoginState.MFA_ENROLLMENT_REQUIRED : LoginState.MFA_REQUIRED,
                    null, challengeToken, enrollmentSecret, otpAuthUri, List.of(), view(access, null));
        }

        return authenticated(access, metadata, List.of(), false);
    }

    @Transactional(noRollbackFor = DomainException.class)
    public LoginOutcome completeMfa(String challengeToken, String code, RequestMetadata metadata) {
        Instant now = clock.instant();
        var challenge = authRepository.findChallenge(tokens.hash(challengeToken))
                .filter(value -> value.consumedAt() == null && value.expiresAt().isAfter(now) && value.attempts() < 5)
                .orElseThrow(this::invalidChallenge);
        var access = accessRepository.findByUserAndOrganization(challenge.userId(), challenge.organizationId())
                .filter(OrganizationAccess::isUsableInternalAccess)
                .filter(value -> value.user().isActiveAt(now))
                .orElseThrow(this::invalidChallenge);

        List<String> recoveryCodes = List.of();
        boolean valid;
        if (challenge.purpose() == AuthChallenge.Purpose.MFA_ENROLL) {
            String secret = cipher.decrypt(challenge.encryptedSecret());
            Long step = totp.verify(secret, code, now, null);
            valid = step != null;
            if (valid) {
                authRepository.saveMfaMethod(access.user().id(), challenge.encryptedSecret(), step, now);
                recoveryCodes = generateRecoveryCodes(access.user().id(), now);
                audit.append(access.organization().id(), access.user().id(), "MFA_ENROLLED",
                        "USER", access.user().id(), Map.of(), Map.of("method", "TOTP"), "API", metadata.ipHash());
            }
        } else {
            var method = authRepository.findMfaMethod(access.user().id()).orElseThrow(this::invalidChallenge);
            if (code != null && code.matches("\\d{6}")) {
                Long step = totp.verify(cipher.decrypt(method.encryptedSecret()), code, now, method.lastUsedStep());
                valid = step != null && authRepository.advanceMfaStep(access.user().id(), method.lastUsedStep(), step);
            } else {
                valid = code != null && authRepository.consumeRecoveryCode(access.user().id(),
                        tokens.hash(normalizeRecoveryCode(code)), now);
            }
        }
        if (!valid) {
            authRepository.incrementChallengeAttempts(challenge.id());
            audit.append(access.organization().id(), access.user().id(), "MFA_FAILED", "USER",
                    access.user().id(), Map.of(), Map.of(), "API", metadata.ipHash());
            throw new DomainException("INVALID_MFA_CODE", "The verification code is invalid or expired.",
                    HttpStatus.UNAUTHORIZED);
        }
        authRepository.consumeChallenge(challenge.id(), now);
        return authenticated(access, metadata, recoveryCodes, true);
    }

    @Transactional
    public SessionResult refresh(TenantPrincipal principal, RequestMetadata metadata) {
        Instant now = clock.instant();
        if (!authRepository.consumeSession(principal.organizationId(), principal.userId(), principal.sessionId(),
                now, now.minus(properties.sessionIdleTimeout()))) throw unauthorized();
        var internal = accessRepository.findByUserAndOrganization(principal.userId(), principal.organizationId())
                .filter(OrganizationAccess::isUsableInternalAccess)
                .filter(value -> value.user().isActiveAt(now));
        SessionResult result;
        if (internal.isPresent()) {
            var access = internal.get();
            if (MfaAssurance.requiredFor(access.permissions()) && !principal.mfaVerified()) throw unauthorized();
            result = newSession(access, metadata, now, principal.mfaVerified());
        } else {
            var access = clientAccess.findByUserAndOrganization(principal.userId(), principal.organizationId())
                    .filter(value -> value.user().isActiveAt(now)).orElseThrow(this::unauthorized);
            String raw = tokens.issue();
            UUID sessionId = UUID.randomUUID();
            authRepository.insertSession(new AuthSession(sessionId, access.organizationId(), access.user().id(),
                    tokens.hash(raw), access.user().credentialVersion(), now, now,
                    now.plus(properties.sessionDuration()), null, null), metadata.ipHash(), metadata.userAgentHash());
            result = new SessionResult(sessionId, raw);
        }
        audit.append(principal.organizationId(), principal.userId(), "SESSION_ROTATED", "AUTH_SESSION",
                result.sessionId(), Map.of(), Map.of(), "API", metadata.ipHash());
        return result;
    }

    @Transactional
    public void logout(TenantPrincipal principal, RequestMetadata metadata) {
        authRepository.revokeSession(principal.sessionId(), clock.instant());
        audit.append(principal.organizationId(), principal.userId(), "AUTH_LOGOUT", "AUTH_SESSION",
                principal.sessionId(), Map.of(), Map.of(), "API", metadata.ipHash());
    }

    @Transactional
    public void forgotPassword(String emailValue, String slugValue, RequestMetadata metadata) {
        String email = normalizeEmail(emailValue);
        String slug = normalizeSlug(slugValue);
        accessRepository.findByEmailAndSlug(email, slug)
                .filter(OrganizationAccess::isUsableInternalAccess)
                .ifPresent(access -> {
                    String raw = tokens.issue();
                    Instant now = clock.instant();
                    authRepository.insertPasswordResetToken(UUID.randomUUID(), access.organization().id(),
                            access.user().id(), tokens.hash(raw), now.plus(properties.tokenDuration()), now);
                    try {
                        notifications.sendPasswordReset(access.user().email(), access.user().displayName(),
                                properties.publicAppUrl() + "/reset-password?token=" + url(raw));
                    } catch (RuntimeException exception) {
                        LOGGER.warn("Password-reset delivery failed for userId={} organizationId={}",
                                access.user().id(), access.organization().id(), exception);
                    }
                    audit.append(access.organization().id(), access.user().id(), "PASSWORD_RESET_REQUESTED",
                            "USER", access.user().id(), Map.of(), Map.of(), "API", metadata.ipHash());
                });
    }

    @Transactional
    public void resetPassword(String rawToken, String password, RequestMetadata metadata) {
        passwordPolicy.validate(password);
        Instant now = clock.instant();
        var token = authRepository.findPasswordResetToken(tokens.hash(rawToken))
                .filter(value -> value.usableAt(now)).orElseThrow(this::invalidSecurityToken);
        if (!authRepository.consumePasswordResetToken(token.id(), now)) throw invalidSecurityToken();
        identityRepository.resetPassword(token.userId(), passwordEncoder.encode(password), now);
        authRepository.revokeAllSessions(token.userId(), now);
        audit.append(token.organizationId(), token.userId(), "PASSWORD_RESET_COMPLETED", "USER",
                token.userId(), Map.of(), Map.of("sessionsRevoked", true), "API", metadata.ipHash());
    }

    @Transactional
    public void verifyEmail(String rawToken, String password, RequestMetadata metadata) {
        passwordPolicy.validate(password);
        Instant now = clock.instant();
        var token = authRepository.findVerificationToken(tokens.hash(rawToken))
                .filter(value -> value.usableAt(now)).orElseThrow(this::invalidSecurityToken);
        if (!authRepository.consumeVerificationToken(token.id(), now)) throw invalidSecurityToken();
        identityRepository.verifyAndSetPassword(token.userId(), passwordEncoder.encode(password), now);
        authRepository.revokeAllSessions(token.userId(), now);
        audit.append(token.organizationId(), token.userId(), "EMAIL_VERIFIED", "USER", token.userId(),
                Map.of(), Map.of("membershipActivated", true), "API", metadata.ipHash());
    }

    @Transactional
    public void acceptInvitation(String rawToken, String password, RequestMetadata metadata) {
        Instant now = clock.instant();
        var invitation = authRepository.findOrganizationInvitation(tokens.hash(rawToken))
                .filter(value -> value.usableAt(now)).orElseThrow(this::invalidSecurityToken);
        var user = identityRepository.findById(invitation.userId()).orElseThrow(this::invalidSecurityToken);
        if (user.emailVerifiedAt() == null || user.status() == com.brainserve.clientonboarding.identity.domain.model.UserAccount.UserStatus.PENDING) {
            passwordPolicy.validate(password);
            identityRepository.verifyAndSetPassword(user.id(), passwordEncoder.encode(password), now);
        } else if (!passwordEncoder.matches(password, user.passwordHash())) {
            throw invalidSecurityToken();
        }
        if (!organizationRepository.activateMembership(invitation.organizationId(), invitation.membershipId(),
                invitation.userId(), now)) {
            throw invalidSecurityToken();
        }
        authRepository.consumeOrganizationInvitation(invitation.id(), now);
        authRepository.revokeAllSessions(invitation.userId(), now);
        audit.append(invitation.organizationId(), invitation.userId(), "ORGANIZATION_INVITATION_ACCEPTED",
                "ORGANIZATION_MEMBERSHIP", invitation.membershipId(), Map.of(), Map.of("status", "ACTIVE"),
                "API", metadata.ipHash());
    }

    @Transactional
    public void resendVerification(String emailValue, String slugValue, RequestMetadata metadata) {
        String email = normalizeEmail(emailValue);
        String slug = normalizeSlug(slugValue);
        accessRepository.findByEmailAndSlug(email, slug)
                .filter(access -> access.membershipStatus() == OrganizationAccess.MembershipStatus.INVITED)
                .ifPresent(access -> {
                    String raw = tokens.issue();
                    Instant now = clock.instant();
                    authRepository.insertOrganizationInvitation(UUID.randomUUID(), access.organization().id(),
                            access.membershipId(), access.user().id(), tokens.hash(raw),
                            now.plus(properties.tokenDuration()), now);
                    try {
                        notifications.sendOrganizationInvitation(access.user().email(), access.user().displayName(),
                                access.organization().name(), properties.publicAppUrl()
                                        + "/accept-invitation?token=" + url(raw));
                    } catch (RuntimeException exception) {
                        LOGGER.warn("Organization-invitation resend delivery failed for membershipId={} organizationId={}",
                                access.membershipId(), access.organization().id(), exception);
                    }
                    audit.append(access.organization().id(), access.user().id(),
                            "ORGANIZATION_INVITATION_RESENT", "ORGANIZATION_MEMBERSHIP",
                            access.membershipId(), Map.of(), Map.of(), "API", metadata.ipHash());
                });
    }

    private LoginOutcome authenticated(OrganizationAccess access, RequestMetadata metadata, List<String> codes,
                                       boolean mfaVerified) {
        SessionResult session = newSession(access, metadata, clock.instant(), mfaVerified);
        audit.append(access.organization().id(), access.user().id(), "AUTH_LOGIN_SUCCESS", "AUTH_SESSION",
                session.sessionId(), Map.of(), Map.of(), "API", metadata.ipHash());
        return new LoginOutcome(LoginState.AUTHENTICATED, session.rawToken(), null, null, null, codes,
                view(access, session.sessionId()));
    }

    private SessionResult newSession(OrganizationAccess access, RequestMetadata metadata, Instant now,
                                     boolean mfaVerified) {
        String raw = tokens.issue();
        UUID sessionId = UUID.randomUUID();
        authRepository.insertSession(new AuthSession(sessionId, access.organization().id(), access.user().id(),
                tokens.hash(raw), access.user().credentialVersion(), now, now,
                now.plus(properties.sessionDuration()), null, mfaVerified ? now : null),
                metadata.ipHash(), metadata.userAgentHash());
        return new SessionResult(sessionId, raw);
    }

    private List<String> generateRecoveryCodes(UUID userId, Instant now) {
        var raw = new ArrayList<String>();
        var stored = new ArrayList<AuthRepository.RecoveryCode>();
        for (int index = 0; index < 8; index++) {
            String code = tokens.recoveryCode();
            raw.add(code);
            stored.add(new AuthRepository.RecoveryCode(UUID.randomUUID(), tokens.hash(normalizeRecoveryCode(code))));
        }
        authRepository.replaceRecoveryCodes(userId, stored, now);
        return List.copyOf(raw);
    }

    private void failedLogin(OrganizationAccess access, Instant now, String ipHash) {
        int failures = access.user().failedLoginCount() + 1;
        Instant lockedUntil = failures >= properties.loginMaxAttempts()
                ? now.plus(properties.loginLockDuration()) : null;
        identityRepository.recordFailedLogin(access.user().id(), properties.loginMaxAttempts(),
                now.plus(properties.loginLockDuration()), now);
        audit.append(access.organization().id(), access.user().id(), "AUTH_LOGIN_FAILED", "USER",
                access.user().id(), Map.of(), Map.of("locked", lockedUntil != null), "API", ipHash);
    }

    private String otpAuthUri(OrganizationAccess access, String secret) {
        String label = url("Client Onboarding:" + access.user().email());
        return "otpauth://totp/" + label + "?secret=" + secret + "&issuer="
                + url("Client Onboarding") + "&algorithm=SHA1&digits=6&period=30";
    }

    private String url(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeSlug(String slug) {
        return slug == null ? "" : slug.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeRecoveryCode(String code) {
        return code == null ? "" : code.replace("-", "").trim().toUpperCase(Locale.ROOT);
    }

    private DomainException unauthorized() {
        return new DomainException("INVALID_CREDENTIALS", "The organization or credentials are invalid.",
                HttpStatus.UNAUTHORIZED);
    }

    private DomainException invalidChallenge() {
        return new DomainException("INVALID_AUTH_CHALLENGE", "The authentication challenge is invalid or expired.",
                HttpStatus.UNAUTHORIZED);
    }

    private DomainException invalidSecurityToken() {
        return new DomainException("INVALID_SECURITY_TOKEN", "The security link is invalid or expired.",
                HttpStatus.BAD_REQUEST);
    }

    private UserView view(OrganizationAccess access, UUID sessionId) {
        return new UserView(access.user().id(), access.organization().id(), access.organization().name(),
                access.organization().slug(), access.user().email(), access.user().displayName(),
                access.roleName(), access.permissions(), sessionId);
    }

    public record LoginCommand(String email, String password, String organizationSlug) { }
    public enum LoginState { AUTHENTICATED, MFA_REQUIRED, MFA_ENROLLMENT_REQUIRED }
    public record LoginOutcome(LoginState state, String rawSessionToken, String challengeToken,
                               String enrollmentSecret, String otpAuthUri, List<String> recoveryCodes,
                               UserView user) { }
    public record SessionResult(UUID sessionId, String rawToken) { }
    public record UserView(UUID id, UUID organizationId, String organizationName, String organizationSlug,
                           String email, String displayName, String role, Set<String> permissions,
                           UUID sessionId) { }
}
