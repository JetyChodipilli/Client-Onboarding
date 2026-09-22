package com.brainserve.clientonboarding.portal.application;

import com.brainserve.clientonboarding.audit.application.AuditService;
import com.brainserve.clientonboarding.auth.application.LoginRateLimiter;
import com.brainserve.clientonboarding.auth.application.PasswordPolicy;
import com.brainserve.clientonboarding.auth.application.SecureTokenService;
import com.brainserve.clientonboarding.auth.application.SecurityNotificationPort;
import com.brainserve.clientonboarding.auth.domain.model.AuthSession;
import com.brainserve.clientonboarding.auth.domain.repository.AuthRepository;
import com.brainserve.clientonboarding.auth.infrastructure.configuration.AuthProperties;
import com.brainserve.clientonboarding.common.error.DomainException;
import com.brainserve.clientonboarding.common.observability.RequestMetadata;
import com.brainserve.clientonboarding.common.security.TenantPrincipal;
import com.brainserve.clientonboarding.identity.domain.model.UserAccount;
import com.brainserve.clientonboarding.identity.domain.repository.IdentityRepository;
import com.brainserve.clientonboarding.onboarding.domain.model.OnboardingInstance;
import com.brainserve.clientonboarding.onboarding.domain.model.OnboardingStepInstance;
import com.brainserve.clientonboarding.onboarding.domain.model.ReadinessPolicy;
import com.brainserve.clientonboarding.onboarding.domain.repository.OnboardingRepository;
import com.brainserve.clientonboarding.portal.domain.model.ClientInvitation;
import com.brainserve.clientonboarding.portal.domain.repository.PortalRepository;
import com.brainserve.clientonboarding.workflow.domain.model.TemplateStep;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClientPortalService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientPortalService.class);
    private static final String INVITE_SCOPE = "INVITE_CLIENT";
    private final PortalRepository portal;
    private final OnboardingRepository onboardings;
    private final IdentityRepository identities;
    private final AuthRepository auth;
    private final PasswordEncoder passwords;
    private final PasswordPolicy passwordPolicy;
    private final SecureTokenService tokens;
    private final SecurityNotificationPort notifications;
    private final LoginRateLimiter rateLimiter;
    private final AuditService audit;
    private final AuthProperties properties;
    private final Clock clock;
    private final String dummyHash;

    public ClientPortalService(PortalRepository portal, OnboardingRepository onboardings,
                               IdentityRepository identities,
                               AuthRepository auth, PasswordEncoder passwords, PasswordPolicy passwordPolicy,
                               SecureTokenService tokens, SecurityNotificationPort notifications,
                               LoginRateLimiter rateLimiter, AuditService audit, AuthProperties properties,
                               Clock clock) {
        this.portal = portal; this.onboardings = onboardings;
        this.identities = identities; this.auth = auth; this.passwords = passwords;
        this.passwordPolicy = passwordPolicy; this.tokens = tokens; this.notifications = notifications;
        this.rateLimiter = rateLimiter; this.audit = audit; this.properties = properties; this.clock = clock;
        this.dummyHash = passwords.encode(tokens.issue());
    }

    @PreAuthorize("hasAuthority('ONBOARDING_INVITE')")
    @Transactional
    public InvitationView invite(TenantPrincipal principal, UUID onboardingId, InviteCommand command,
                                 String idempotencyKey, RequestMetadata metadata) {
        String key = idempotencyKey(idempotencyKey);
        String fingerprint = tokens.hash(onboardingId + "|" + command.contactId() + "|" + command.role());
        if (key != null) {
            onboardings.lockTenantCommands(principal.organizationId());
            var prior = onboardings.findIdempotentCommand(principal.organizationId(), INVITE_SCOPE, key);
            if (prior.isPresent()) {
                if (!prior.get().requestFingerprint().equals(fingerprint)) throw idempotencyConflict();
                return view(portal.findInvitation(principal.organizationId(), prior.get().resourceId())
                        .orElseThrow(this::notFound));
            }
        }
        var target = portal.findInvitationTarget(principal.organizationId(), onboardingId, command.contactId())
                .orElseThrow(this::notFound);
        if (!List.of("DRAFT", "INVITED", "IN_PROGRESS").contains(target.onboardingStatus())) {
            throw new DomainException("ONBOARDING_NOT_INVITABLE",
                    "This onboarding can no longer accept client invitations.", HttpStatus.CONFLICT);
        }
        Instant now = clock.instant();
        String raw = tokens.issue();
        ClientInvitation invitation = new ClientInvitation(UUID.randomUUID(), principal.organizationId(),
                target.clientId(), target.contactId(), target.projectId(), target.onboardingId(),
                normalizeEmail(target.contactEmail()), command.role(), tokens.hash(raw),
                ClientInvitation.Status.PENDING, ClientInvitation.DeliveryStatus.PENDING,
                now.plus(properties.tokenDuration()), null, null, null, null, 0, now, now, 0);
        portal.insertInvitation(invitation, principal.userId());
        if ("DRAFT".equals(target.onboardingStatus())) {
            if (!onboardings.updateStatus(principal.organizationId(), onboardingId, OnboardingInstance.Status.DRAFT,
                    OnboardingInstance.Status.INVITED, target.onboardingVersion(), principal.userId(), now)) {
                throw conflict();
            }
        }
        if (key != null) onboardings.insertIdempotency(principal.organizationId(), INVITE_SCOPE, key,
                invitation.id(), fingerprint, now);
        deliver(target, invitation, raw, principal.userId());
        audit.append(principal.organizationId(), principal.userId(), "ONBOARDING_INVITED", "CLIENT_INVITATION",
                invitation.id(), Map.of(), Map.of("onboardingId", onboardingId, "contactId", command.contactId(),
                        "role", command.role()), "API", metadata.ipHash());
        return view(portal.findInvitation(principal.organizationId(), invitation.id()).orElseThrow());
    }

    @PreAuthorize("hasAuthority('ONBOARDING_INVITE')")
    public List<InvitationView> invitations(TenantPrincipal principal, UUID onboardingId) {
        onboardings.findById(principal.organizationId(), onboardingId).orElseThrow(this::notFound);
        return portal.findInvitations(principal.organizationId(), onboardingId).stream().map(this::view).toList();
    }

    @PreAuthorize("hasAuthority('ONBOARDING_INVITE')")
    @Transactional
    public InvitationView resend(TenantPrincipal principal, UUID invitationId, long version,
                                 RequestMetadata metadata) {
        ClientInvitation current = portal.findInvitation(principal.organizationId(), invitationId)
                .orElseThrow(this::notFound);
        if (current.status() != ClientInvitation.Status.PENDING) throw invitationUnavailable();
        var target = portal.findInvitationTarget(principal.organizationId(), current.onboardingId(), current.contactId())
                .orElseThrow(this::notFound);
        String raw = tokens.issue();
        Instant now = clock.instant();
        if (!portal.rotateInvitation(principal.organizationId(), invitationId, tokens.hash(raw),
                now.plus(properties.tokenDuration()), version, principal.userId(), now)) throw conflict();
        ClientInvitation rotated = portal.findInvitation(principal.organizationId(), invitationId).orElseThrow();
        deliver(target, rotated, raw, principal.userId());
        audit.append(principal.organizationId(), principal.userId(), "CLIENT_INVITATION_RESENT", "CLIENT_INVITATION",
                invitationId, Map.of("version", current.version()), Map.of("resendCount", current.resendCount() + 1),
                "API", metadata.ipHash());
        return view(portal.findInvitation(principal.organizationId(), invitationId).orElseThrow());
    }

    @PreAuthorize("hasAuthority('ONBOARDING_INVITE')")
    @Transactional
    public InvitationView revoke(TenantPrincipal principal, UUID invitationId, long version,
                                 RequestMetadata metadata) {
        ClientInvitation current = portal.findInvitation(principal.organizationId(), invitationId)
                .orElseThrow(this::notFound);
        if (!portal.revokeInvitation(principal.organizationId(), invitationId, version, principal.userId(),
                clock.instant())) throw conflict();
        audit.append(principal.organizationId(), principal.userId(), "CLIENT_INVITATION_REVOKED", "CLIENT_INVITATION",
                invitationId, Map.of("status", current.status()), Map.of("status", "REVOKED"),
                "API", metadata.ipHash());
        return view(portal.findInvitation(principal.organizationId(), invitationId).orElseThrow());
    }

    public PublicInvitation inspect(String rawToken) {
        ClientInvitation invitation = token(rawToken);
        var target = portal.findInvitationTarget(invitation.organizationId(), invitation.onboardingId(),
                invitation.contactId()).orElseThrow(this::invalidToken);
        return new PublicInvitation(invitation.usableAt(clock.instant()) ? "VALID" : publicState(invitation),
                target.organizationName(), target.organizationSlug(), target.clientName(), target.projectName(),
                target.contactName(), invitation.invitedEmail(), invitation.expiresAt());
    }

    @Transactional
    public AcceptanceView accept(String rawToken, String password, RequestMetadata metadata) {
        passwordPolicy.validate(password);
        Instant now = clock.instant();
        ClientInvitation invitation = token(rawToken);
        if (!invitation.usableAt(now)) throw invalidToken();
        var target = portal.findInvitationTarget(invitation.organizationId(), invitation.onboardingId(),
                invitation.contactId()).orElseThrow(this::invalidToken);
        String email = normalizeEmail(invitation.invitedEmail());
        UserAccount user = identities.findByEmail(email).orElse(null);
        if (user == null) {
            user = new UserAccount(UUID.randomUUID(), email, target.contactName(), passwords.encode(password),
                    UserAccount.PrincipalType.CLIENT, UserAccount.UserStatus.ACTIVE, now, 0, null, 0, 0);
            identities.insert(user, now, user.id());
        } else {
            if (user.principalType() != UserAccount.PrincipalType.CLIENT) throw invalidToken();
            if (user.status() == UserAccount.UserStatus.PENDING || user.emailVerifiedAt() == null) {
                identities.verifyAndSetPassword(user.id(), passwords.encode(password), now);
                user = identities.findById(user.id()).orElseThrow(this::invalidToken);
            } else if (!user.isActiveAt(now) || !passwords.matches(password, user.passwordHash())) {
                throw invalidToken();
            }
        }
        Optional<PortalRepository.ClientUserLink> existing = portal.findClientUserLink(invitation.organizationId(), user.id());
        if (existing.isPresent() && !existing.get().clientId().equals(invitation.clientId())) throw invalidToken();
        UUID clientUserId = portal.activateClientUser(invitation, user.id(), user.id(), now);
        portal.grantProjectAccess(invitation.organizationId(), invitation.clientId(), clientUserId,
                invitation.projectId(), user.id(), now);
        if (!portal.acceptInvitation(invitation.organizationId(), invitation.id(), invitation.version(),
                user.id(), user.id(), now)) throw invalidToken();
        OnboardingInstance onboarding = onboardings.findById(invitation.organizationId(), invitation.onboardingId())
                .orElseThrow(this::invalidToken);
        if (onboarding.status() == OnboardingInstance.Status.INVITED
                && !onboardings.updateStatus(invitation.organizationId(), onboarding.id(), onboarding.status(),
                OnboardingInstance.Status.IN_PROGRESS, onboarding.version(), user.id(), now)) throw conflict();
        audit.append(invitation.organizationId(), user.id(), "CLIENT_INVITATION_ACCEPTED", "CLIENT_INVITATION",
                invitation.id(), Map.of("status", "PENDING"), Map.of("status", "ACCEPTED",
                        "projectId", invitation.projectId()), "API", metadata.ipHash());
        return new AcceptanceView("Invitation accepted. Sign in to continue.", target.organizationSlug(),
                target.projectName());
    }

    @Transactional(noRollbackFor = DomainException.class)
    public LoginResult login(String emailValue, String password, String slugValue, RequestMetadata metadata) {
        Instant now = clock.instant();
        String email = normalizeEmail(emailValue);
        String slug = slugValue == null ? "" : slugValue.trim().toLowerCase(Locale.ROOT);
        String subjectKey = tokens.hash(metadata.ipHash() + "|client|" + email + "|" + slug);
        rateLimiter.check(metadata.ipHash(), subjectKey);
        var access = portal.findClientAccess(email, slug).orElse(null);
        String hash = access == null ? dummyHash : access.user().passwordHash();
        boolean matches = passwords.matches(password, hash);
        if (access == null || !matches || !access.user().isActiveAt(now)) {
            if (access != null && !matches) identities.recordFailedLogin(access.user().id(),
                    properties.loginMaxAttempts(), now.plus(properties.loginLockDuration()), now);
            rateLimiter.failure(metadata.ipHash(), subjectKey);
            throw new DomainException("INVALID_CREDENTIALS", "The organization or credentials are invalid.",
                    HttpStatus.UNAUTHORIZED);
        }
        identities.clearFailedLogins(access.user().id(), now);
        rateLimiter.success(subjectKey);
        String raw = tokens.issue();
        UUID sessionId = UUID.randomUUID();
        auth.insertSession(new AuthSession(sessionId, access.organizationId(), access.user().id(), tokens.hash(raw),
                access.user().credentialVersion(), now, now, now.plus(properties.sessionDuration()), null, null),
                metadata.ipHash(), metadata.userAgentHash());
        audit.append(access.organizationId(), access.user().id(), "CLIENT_AUTH_LOGIN_SUCCESS", "AUTH_SESSION",
                sessionId, Map.of(), Map.of("scope", "CLIENT_PORTAL"), "API", metadata.ipHash());
        return new LoginResult(raw, new ClientUserView(access.user().id(), access.organizationId(),
                access.organizationName(), access.organizationSlug(), access.clientId(), access.clientName(),
                access.user().email(), access.user().displayName(), access.clientRole(), access.permissions(),
                sessionId));
    }

    @Transactional
    public void forgotPassword(String emailValue, String slugValue, RequestMetadata metadata) {
        String email = normalizeEmail(emailValue);
        String slug = slugValue == null ? "" : slugValue.trim().toLowerCase(Locale.ROOT);
        portal.findClientAccess(email, slug).ifPresent(access -> {
            String raw = tokens.issue();
            Instant now = clock.instant();
            auth.insertPasswordResetToken(UUID.randomUUID(), access.organizationId(), access.user().id(),
                    tokens.hash(raw), now.plus(properties.tokenDuration()), now);
            notifications.sendPasswordReset(access.user().email(), access.user().displayName(),
                    properties.publicAppUrl() + "/client/reset-password?token="
                            + URLEncoder.encode(raw, StandardCharsets.UTF_8));
            audit.append(access.organizationId(), access.user().id(), "CLIENT_PASSWORD_RESET_REQUESTED",
                    "USER", access.user().id(), Map.of(), Map.of("scope", "CLIENT_PORTAL"),
                    "API", metadata.ipHash());
        });
    }

    @PreAuthorize("hasAuthority('CLIENT_PORTAL_READ')")
    public List<PortalProjectView> projectList(TenantPrincipal principal) {
        return portal.findPortalProjects(principal.organizationId(), principal.membershipId()).stream()
                .map(project -> summary(principal, project)).toList();
    }

    @PreAuthorize("hasAuthority('CLIENT_PORTAL_READ')")
    public PortalDashboard dashboard(TenantPrincipal principal, UUID projectId) {
        PortalRepository.PortalProject project = portal.findPortalProject(principal.organizationId(),
                principal.membershipId(), projectId).orElseThrow(this::notFound);
        OnboardingInstance onboarding = onboardings.findById(principal.organizationId(), project.onboardingId())
                .orElseThrow(this::notFound);
        List<OnboardingStepInstance> all = onboardings.findSteps(principal.organizationId(), onboarding.id());
        List<OnboardingStepInstance> visible = clientSteps(principal, all);
        List<PortalStep> steps = visible.stream().map(step -> portalStep(step, visible)).toList();
        PortalStep next = steps.stream().filter(step -> "YOUR_ACTION".equals(step.waitingFor())).findFirst()
                .orElse(null);
        String waiting = next == null && steps.stream().anyMatch(step -> "OUR_TEAM".equals(step.waitingFor()))
                ? "OUR_TEAM" : next == null ? "NONE" : "YOU";
        String status = next != null ? "ACTION_REQUIRED" : "OUR_TEAM".equals(waiting) ? "WAITING" :
                onboarding.ready() ? "READY" : onboarding.status().name();
        String helpEmail = portal.findHelpEmail(principal.organizationId(), projectId).orElse(null);
        return new PortalDashboard(project.projectId(), project.projectName(), project.projectStatus(),
                project.clientName(), onboarding.id(), onboarding.status().name(), status,
                ReadinessPolicy.progress(visible), next, waiting,
                next == null ? ("OUR_TEAM".equals(waiting) ? "Your team is reviewing the current work."
                        : null) : next.blockingReason(), helpEmail,
                helpEmail == null ? "Contact your project team through your usual support channel."
                        : "Email " + helpEmail + " for help.", steps);
    }

    @PreAuthorize("hasAuthority('CLIENT_PORTAL_STEP_UPDATE')")
    @Transactional
    public PortalDashboard transition(TenantPrincipal principal, UUID projectId, UUID stepId,
                                      ClientStepCommand command, RequestMetadata metadata) {
        PortalRepository.PortalProject project = portal.findPortalProject(principal.organizationId(),
                principal.membershipId(), projectId).orElseThrow(this::notFound);
        OnboardingStepInstance step = onboardings.findStep(principal.organizationId(), stepId)
                .filter(value -> value.onboardingId().equals(project.onboardingId()) && value.clientVisible()
                        && value.applicable())
                .filter(value -> principal.hasPermission("CLIENT_PORTAL_ADMIN") || value.assignedRole() == null
                        || "CLIENT_MEMBER".equalsIgnoreCase(value.assignedRole()))
                .orElseThrow(this::notFound);
        if (!List.of(TemplateStep.StepType.WELCOME, TemplateStep.StepType.INSTRUCTION,
                TemplateStep.StepType.EXTERNAL_LINK, TemplateStep.StepType.VIDEO_GUIDE).contains(step.stepType())) {
            throw new DomainException("STEP_HANDLER_UNAVAILABLE",
                    "This step needs its dedicated secure collection flow.", HttpStatus.CONFLICT);
        }
        OnboardingStepInstance.Status target = command.targetStatus();
        boolean allowed = (step.status() == OnboardingStepInstance.Status.AVAILABLE
                && target == OnboardingStepInstance.Status.IN_PROGRESS)
                || (step.status() == OnboardingStepInstance.Status.IN_PROGRESS
                && target == (step.requiresReview() ? OnboardingStepInstance.Status.SUBMITTED
                                                    : OnboardingStepInstance.Status.COMPLETED));
        if (!allowed) throw new DomainException("INVALID_STEP_TRANSITION",
                "The requested client step transition is not allowed.", HttpStatus.CONFLICT);
        Instant now = clock.instant();
        if (!onboardings.updateStepStatus(principal.organizationId(), stepId, step.status(), target,
                command.version(), principal.userId(), now)) throw conflict();
        onboardings.refreshAvailability(principal.organizationId(), step.onboardingId(), principal.userId(), now);
        OnboardingInstance onboarding = onboardings.findById(principal.organizationId(), step.onboardingId())
                .orElseThrow(this::notFound);
        List<OnboardingStepInstance> updated = onboardings.findSteps(principal.organizationId(), step.onboardingId());
        if (!onboardings.updateReadiness(principal.organizationId(), onboarding.id(), ReadinessPolicy.ready(updated),
                onboarding.version(), principal.userId(), now)) throw conflict();
        audit.append(principal.organizationId(), principal.userId(), "CLIENT_STEP_TRANSITIONED", "ONBOARDING_STEP",
                stepId, Map.of("status", step.status()), Map.of("status", target), "API", metadata.ipHash());
        return dashboard(principal, projectId);
    }

    private PortalProjectView summary(TenantPrincipal principal, PortalRepository.PortalProject project) {
        List<OnboardingStepInstance> steps = clientSteps(principal,
                onboardings.findSteps(principal.organizationId(), project.onboardingId()));
        long pending = steps.stream()
                .filter(step -> step.status() != OnboardingStepInstance.Status.COMPLETED
                        && step.status() != OnboardingStepInstance.Status.SKIPPED)
                .count();
        return new PortalProjectView(project.projectId(), project.projectName(), project.projectStatus(),
                project.clientName(), project.onboardingStatus(), ReadinessPolicy.progress(steps), pending);
    }

    private List<OnboardingStepInstance> clientSteps(TenantPrincipal principal,
                                                      List<OnboardingStepInstance> steps) {
        return steps.stream().filter(step -> step.applicable() && step.clientVisible())
                .filter(step -> principal.hasPermission("CLIENT_PORTAL_ADMIN") || step.assignedRole() == null
                        || "CLIENT_MEMBER".equalsIgnoreCase(step.assignedRole()))
                .sorted(Comparator.comparingInt(OnboardingStepInstance::displayOrder)).toList();
    }

    private PortalStep portalStep(OnboardingStepInstance step, List<OnboardingStepInstance> all) {
        String waitingFor;
        String reason = null;
        if (List.of(OnboardingStepInstance.Status.AVAILABLE, OnboardingStepInstance.Status.IN_PROGRESS,
                OnboardingStepInstance.Status.NEEDS_REVISION).contains(step.status())) waitingFor = "YOUR_ACTION";
        else if (List.of(OnboardingStepInstance.Status.SUBMITTED, OnboardingStepInstance.Status.UNDER_REVIEW)
                .contains(step.status())) waitingFor = "OUR_TEAM";
        else if (step.status() == OnboardingStepInstance.Status.LOCKED) {
            Optional<OnboardingStepInstance> openDependency = step.dependencyStepInstanceIds().stream()
                    .map(id -> all.stream().filter(item -> item.id().equals(id)).findFirst().orElse(null))
                    .filter(java.util.Objects::nonNull)
                    .filter(item -> item.status() != OnboardingStepInstance.Status.COMPLETED).findFirst();
            if (openDependency.isPresent() && openDependency.get().clientVisible()) {
                waitingFor = "YOUR_ACTION";
                reason = "Complete “" + openDependency.get().name() + "” first.";
            } else {
                waitingFor = "OUR_TEAM";
                reason = "Your team is completing a prerequisite.";
            }
        } else waitingFor = "NONE";
        boolean actionable = List.of(TemplateStep.StepType.WELCOME, TemplateStep.StepType.INSTRUCTION,
                TemplateStep.StepType.EXTERNAL_LINK, TemplateStep.StepType.VIDEO_GUIDE).contains(step.stepType())
                && (step.status() == OnboardingStepInstance.Status.AVAILABLE
                    || step.status() == OnboardingStepInstance.Status.IN_PROGRESS);
        return new PortalStep(step.id(), step.name(), step.description(), step.stepType().name(),
                step.status().name(), step.required(), step.blocking(), step.dueAt(), waitingFor, reason,
                actionable, step.version());
    }

    private void deliver(PortalRepository.InvitationTarget target, ClientInvitation invitation,
                         String raw, UUID actorId) {
        Instant now = clock.instant();
        try {
            String link = properties.publicAppUrl() + "/client/accept-invitation?token="
                    + URLEncoder.encode(raw, StandardCharsets.UTF_8);
            notifications.sendClientInvitation(invitation.invitedEmail(), target.contactName(),
                    target.organizationName(), target.projectName(), link);
            portal.markDelivery(invitation.organizationId(), invitation.id(), ClientInvitation.DeliveryStatus.SENT,
                    null, actorId, now);
        } catch (RuntimeException exception) {
            portal.markDelivery(invitation.organizationId(), invitation.id(), ClientInvitation.DeliveryStatus.FAILED,
                    "Delivery failed; resend is available.", actorId, now);
            LOGGER.warn("Client invitation delivery failed for invitationId={} organizationId={}",
                    invitation.id(), invitation.organizationId(), exception);
        }
    }

    private ClientInvitation token(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > 200) throw invalidToken();
        return portal.findInvitationByTokenHash(tokens.hash(raw)).orElseThrow(this::invalidToken);
    }
    private String publicState(ClientInvitation value) {
        if (value.status() == ClientInvitation.Status.REVOKED) return "REVOKED";
        if (value.status() == ClientInvitation.Status.ACCEPTED) return "ACCEPTED";
        return value.expiresAt().isAfter(clock.instant()) ? "INVALID" : "EXPIRED";
    }
    private String normalizeEmail(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
    private String idempotencyKey(String value) {
        if (value == null || value.isBlank()) return null;
        String clean = value.trim();
        if (clean.length() < 8 || clean.length() > 120 || !clean.matches("[A-Za-z0-9._:-]+")) {
            throw new DomainException("INVALID_IDEMPOTENCY_KEY", "Idempotency-Key must contain 8 to 120 safe characters.", HttpStatus.BAD_REQUEST);
        }
        return clean;
    }
    private InvitationView view(ClientInvitation value) {
        String effective = value.status() == ClientInvitation.Status.PENDING
                ? (value.expiresAt().isAfter(clock.instant()) ? "PENDING" : "EXPIRED")
                : value.status().name();
        return new InvitationView(value.id(), value.onboardingId(), value.contactId(), value.invitedEmail(),
                value.clientRole(), value.status(), value.deliveryStatus(), effective, value.expiresAt(),
                value.sentAt(), value.acceptedAt(), value.resendCount(), value.version());
    }
    private DomainException notFound() { return new DomainException("RESOURCE_NOT_FOUND", "Requested resource was not found.", HttpStatus.NOT_FOUND); }
    private DomainException conflict() { return new DomainException("OPTIMISTIC_LOCK_CONFLICT", "The resource changed. Refresh and try again.", HttpStatus.CONFLICT); }
    private DomainException invalidToken() { return new DomainException("INVALID_INVITATION", "This invitation is invalid, expired, used, or revoked.", HttpStatus.GONE); }
    private DomainException invitationUnavailable() { return new DomainException("INVITATION_UNAVAILABLE", "This invitation can no longer be changed.", HttpStatus.CONFLICT); }
    private DomainException idempotencyConflict() { return new DomainException("IDEMPOTENCY_KEY_REUSED", "This idempotency key was already used for a different request.", HttpStatus.CONFLICT); }

    public record InviteCommand(UUID contactId, ClientInvitation.ClientRole role) { }
    public record ClientStepCommand(OnboardingStepInstance.Status targetStatus, long version) { }
    public record InvitationView(UUID id, UUID onboardingId, UUID contactId, String email,
                                 ClientInvitation.ClientRole role, ClientInvitation.Status status,
                                 ClientInvitation.DeliveryStatus deliveryStatus, String effectiveStatus,
                                 Instant expiresAt, Instant sentAt, Instant acceptedAt, int resendCount,
                                 long version) { }
    public record PublicInvitation(String status, String organizationName, String organizationSlug,
                                   String clientName, String projectName, String contactName, String email,
                                   Instant expiresAt) { }
    public record AcceptanceView(String message, String organizationSlug, String projectName) { }
    public record LoginResult(String rawSessionToken, ClientUserView user) { }
    public record ClientUserView(UUID id, UUID organizationId, String organizationName, String organizationSlug,
                                 UUID clientId, String clientName, String email, String displayName, String role,
                                 java.util.Set<String> permissions, UUID sessionId) { }
    public record PortalProjectView(UUID id, String name, String projectStatus, String clientName,
                                    String onboardingStatus, int progress, long pendingRequirements) { }
    public record PortalStep(UUID id, String name, String description, String type, String status,
                             boolean required, boolean blocking, Instant deadline, String waitingFor,
                             String blockingReason, boolean actionable, long version) { }
    public record PortalDashboard(UUID projectId, String projectName, String projectStatus, String clientName,
                                  UUID onboardingId, String onboardingStatus, String currentStatus, int progress,
                                  PortalStep nextAction, String waitingFor, String blockingReason,
                                  String helpEmail, String availableHelp, List<PortalStep> steps) { }
}
