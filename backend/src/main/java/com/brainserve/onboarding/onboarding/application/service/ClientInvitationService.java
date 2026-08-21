package com.brainserve.onboarding.onboarding.application.service;

import com.brainserve.onboarding.audit.application.service.AuditService;
import com.brainserve.onboarding.auth.api.response.AuthResponse;
import com.brainserve.onboarding.auth.application.service.AuthRateLimiter;
import com.brainserve.onboarding.auth.application.service.ClientAccountActivationService;
import com.brainserve.onboarding.auth.application.service.ClientAuthService;
import com.brainserve.onboarding.auth.application.service.SecurityMailService;
import com.brainserve.onboarding.client.application.service.ClientContactLookupService;
import com.brainserve.onboarding.common.activity.ActivityTimelineService;
import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.common.event.OutboxService;
import com.brainserve.onboarding.common.security.NetworkSource;
import com.brainserve.onboarding.common.security.SecurityProperties;
import com.brainserve.onboarding.common.security.TenantPrincipal;
import com.brainserve.onboarding.common.util.CryptoSupport;
import com.brainserve.onboarding.identity.application.service.IdentityAccountService;
import com.brainserve.onboarding.onboarding.api.request.AcceptClientInvitationRequest;
import com.brainserve.onboarding.onboarding.api.request.CreateClientInvitationRequest;
import com.brainserve.onboarding.onboarding.api.response.ClientInvitationContactResponse;
import com.brainserve.onboarding.onboarding.api.response.ClientInvitationPreviewResponse;
import com.brainserve.onboarding.onboarding.api.response.ClientInvitationResponse;
import com.brainserve.onboarding.onboarding.domain.model.ClientInvitation;
import com.brainserve.onboarding.onboarding.domain.model.ClientInvitationStatus;
import com.brainserve.onboarding.onboarding.domain.model.OnboardingInstance;
import com.brainserve.onboarding.onboarding.domain.model.OnboardingStatus;
import com.brainserve.onboarding.onboarding.infrastructure.persistence.ClientInvitationRepository;
import com.brainserve.onboarding.onboarding.infrastructure.persistence.OnboardingInstanceRepository;
import com.brainserve.onboarding.organization.application.service.OrganizationIdentityService;
import com.brainserve.onboarding.project.application.service.ProjectWorkflowAccessService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ClientInvitationService {
    private static final Logger log = LoggerFactory.getLogger(ClientInvitationService.class);
    private static final Set<OnboardingStatus> INVITABLE = Set.of(
            OnboardingStatus.DRAFT, OnboardingStatus.INVITED, OnboardingStatus.IN_PROGRESS,
            OnboardingStatus.NEEDS_REVISION, OnboardingStatus.AWAITING_INTERNAL_REVIEW, OnboardingStatus.PAUSED);

    private final ClientInvitationRepository invitations;
    private final OnboardingInstanceRepository onboardings;
    private final ProjectWorkflowAccessService projects;
    private final ClientContactLookupService contacts;
    private final ClientAccountActivationService activation;
    private final ClientAuthService clientAuth;
    private final IdentityAccountService users;
    private final OrganizationIdentityService organizations;
    private final SecurityMailService mail;
    private final AuthRateLimiter rateLimiter;
    private final ActivityTimelineService activity;
    private final AuditService audit;
    private final OutboxService outbox;
    private final SecurityProperties security;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public ClientInvitationService(ClientInvitationRepository invitations,
                                   OnboardingInstanceRepository onboardings,
                                   ProjectWorkflowAccessService projects,
                                   ClientContactLookupService contacts,
                                   ClientAccountActivationService activation,
                                   ClientAuthService clientAuth,
                                   IdentityAccountService users,
                                   OrganizationIdentityService organizations,
                                   SecurityMailService mail,
                                   AuthRateLimiter rateLimiter,
                                   ActivityTimelineService activity,
                                   AuditService audit,
                                   OutboxService outbox,
                                   SecurityProperties security,
                                   Clock clock,
                                   TransactionTemplate transactions) {
        this.invitations=invitations; this.onboardings=onboardings; this.projects=projects; this.contacts=contacts;
        this.activation=activation; this.clientAuth=clientAuth; this.users=users; this.organizations=organizations;
        this.mail=mail; this.rateLimiter=rateLimiter; this.activity=activity; this.audit=audit; this.outbox=outbox;
        this.security=security; this.clock=clock; this.transactions=transactions;
    }

    public ClientInvitationResponse create(TenantPrincipal principal, UUID onboardingId, CreateClientInvitationRequest request,
                                           HttpServletRequest servletRequest) {
        InvitationDispatch dispatch = transactions.execute(status -> createInTransaction(principal, onboardingId, request, servletRequest));
        if (dispatch == null) throw new IllegalStateException("Invitation transaction returned no result");
        deliver(dispatch, principal.organizationId(), principal.userId(), servletRequest);
        return dispatch.response();
    }

    @Transactional
    protected InvitationDispatch createInTransaction(TenantPrincipal principal, UUID onboardingId,
                                                     CreateClientInvitationRequest request, HttpServletRequest servletRequest) {
        Instant now = clock.instant();
        OnboardingInstance onboarding = requireForUpdate(principal.organizationId(), onboardingId);
        requireInvitable(onboarding);
        var project = projects.require(principal.organizationId(), onboarding.getProjectId());
        invitations.expirePendingForOnboarding(principal.organizationId(), onboardingId, now, ClientInvitationStatus.PENDING, ClientInvitationStatus.EXPIRED);
        if (invitations.findByOrganizationIdAndOnboardingIdAndContactIdAndStatus(
                principal.organizationId(), onboardingId, request.contactId(), ClientInvitationStatus.PENDING).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "CLIENT_INVITATION_PENDING",
                    "This contact already has a pending invitation. Resend or revoke it instead.");
        }
        var contact = contacts.requireForClient(principal.organizationId(), project.clientId(), request.contactId());
        String raw = CryptoSupport.randomUrlToken(32);
        ClientInvitation invitation = invitations.saveAndFlush(new ClientInvitation(UUID.randomUUID(), principal.organizationId(),
                onboardingId, project.id(), project.clientId(), contact.id(), contact.email(), contact.normalizedEmail(),
                contact.displayName(), request.accessLevel(), CryptoSupport.sha256Hex(raw), now.plus(security.clientInvitationTtl()),
                principal.userId(), now));
        OnboardingStatus before = onboarding.getStatus();
        if (before == OnboardingStatus.DRAFT) {
            onboarding.transitionTo(OnboardingStatus.INVITED, principal.userId(), now);
            onboardings.saveAndFlush(onboarding);
        }
        activity.record(principal.organizationId(), project.clientId(), project.id(), principal.userId(), "CLIENT_INVITATION_CREATED",
                "CLIENT_INVITATION", invitation.getId(), "Client portal invitation created", Map.of("contactId", contact.id()));
        audit.record(principal.organizationId(), principal.userId(), "CLIENT_INVITATION_CREATED", "CLIENT_INVITATION", invitation.getId(), null,
                Map.of("onboardingId", onboardingId, "projectId", project.id(), "contactId", contact.id(),
                        "accessLevel", request.accessLevel(), "expiresAt", invitation.getExpiresAt()), servletRequest);
        outbox.record(principal.organizationId(), "CLIENT_INVITATION_CREATED", "CLIENT_INVITATION", invitation.getId(),
                Map.of("onboardingId", onboardingId, "projectId", project.id(), "contactId", contact.id()));
        if (before == OnboardingStatus.DRAFT) {
            outbox.record(principal.organizationId(), "ONBOARDING_INVITED", "ONBOARDING", onboardingId,
                    Map.of("onboardingId", onboardingId, "projectId", project.id(), "invitationId", invitation.getId()));
        }
        String orgName = organizations.findOrganizationById(principal.organizationId()).map(value -> value.getName()).orElse("Your service provider");
        return new InvitationDispatch(raw, contact.email(), orgName, project.name(), map(invitation));
    }

    public ClientInvitationResponse resend(TenantPrincipal principal, UUID onboardingId, UUID invitationId,
                                           long expectedVersion, HttpServletRequest request) {
        InvitationDispatch dispatch = transactions.execute(status -> {
            Instant now = clock.instant();
            OnboardingInstance onboarding = requireForUpdate(principal.organizationId(), onboardingId);
            requireInvitable(onboarding);
            invitations.expirePendingById(principal.organizationId(), invitationId, now, ClientInvitationStatus.PENDING, ClientInvitationStatus.EXPIRED);
            ClientInvitation invitation = invitations.findForUpdate(principal.organizationId(), invitationId).orElseThrow(ClientInvitationService::notFound);
            if (!invitation.getOnboardingId().equals(onboardingId)) throw notFound();
            requireVersion(invitation, expectedVersion);
            if (invitation.getStatus()!=ClientInvitationStatus.PENDING) throw invalidState("Only pending invitations can be resent.");
            String raw = CryptoSupport.randomUrlToken(32);
            invitation.rotate(CryptoSupport.sha256Hex(raw), now.plus(security.clientInvitationTtl()), now);
            invitations.saveAndFlush(invitation);
            var project = projects.require(principal.organizationId(), invitation.getProjectId());
            audit.record(principal.organizationId(), principal.userId(), "CLIENT_INVITATION_RESENT", "CLIENT_INVITATION", invitation.getId(), null,
                    Map.of("expiresAt", invitation.getExpiresAt(), "resendCount", invitation.getResendCount()), request);
            outbox.record(principal.organizationId(), "CLIENT_INVITATION_RESENT", "CLIENT_INVITATION", invitation.getId(),
                    Map.of("onboardingId", onboardingId, "projectId", project.id()));
            String orgName=organizations.findOrganizationById(principal.organizationId()).map(value->value.getName()).orElse("Your service provider");
            return new InvitationDispatch(raw, invitation.getEmail(), orgName, project.name(), map(invitation));
        });
        if (dispatch==null) throw new IllegalStateException("Invitation transaction returned no result");
        deliver(dispatch, principal.organizationId(), principal.userId(), request);
        return dispatch.response();
    }

    @Transactional
    public ClientInvitationResponse revoke(TenantPrincipal principal, UUID onboardingId, UUID invitationId,
                                           long expectedVersion, HttpServletRequest request) {
        Instant now=clock.instant();
        requireForUpdate(principal.organizationId(), onboardingId);
        invitations.expirePendingById(principal.organizationId(), invitationId, now, ClientInvitationStatus.PENDING, ClientInvitationStatus.EXPIRED);
        ClientInvitation invitation=invitations.findForUpdate(principal.organizationId(), invitationId).orElseThrow(ClientInvitationService::notFound);
        if(!invitation.getOnboardingId().equals(onboardingId)) throw notFound();
        requireVersion(invitation, expectedVersion);
        try { invitation.revoke(now); } catch(IllegalStateException ex){ throw invalidState(ex.getMessage()); }
        invitations.saveAndFlush(invitation);
        audit.record(principal.organizationId(), principal.userId(), "CLIENT_INVITATION_REVOKED", "CLIENT_INVITATION", invitationId, null,
                Map.of("onboardingId",onboardingId), request);
        outbox.record(principal.organizationId(), "CLIENT_INVITATION_REVOKED", "CLIENT_INVITATION", invitationId,
                Map.of("onboardingId",onboardingId,"projectId",invitation.getProjectId()));
        return map(invitation);
    }

    @Transactional
    public List<ClientInvitationResponse> list(TenantPrincipal principal, UUID onboardingId) {
        require(principal.organizationId(), onboardingId);
        invitations.expirePendingForOnboarding(principal.organizationId(), onboardingId, clock.instant(), ClientInvitationStatus.PENDING, ClientInvitationStatus.EXPIRED);
        return invitations.findAllByOrganizationIdAndOnboardingIdOrderByCreatedAtDescIdDesc(principal.organizationId(), onboardingId)
                .stream().map(ClientInvitationService::map).toList();
    }

    @Transactional(readOnly=true)
    public List<ClientInvitationContactResponse> contacts(TenantPrincipal principal, UUID onboardingId) {
        OnboardingInstance onboarding=require(principal.organizationId(), onboardingId);
        var project=projects.require(principal.organizationId(), onboarding.getProjectId());
        return contacts.listForClient(principal.organizationId(), project.clientId(), 100).stream()
                .map(value->new ClientInvitationContactResponse(value.id(),value.displayName(),value.email())).toList();
    }

    @Transactional(readOnly=true)
    public ClientInvitationPreviewResponse preview(String rawToken, HttpServletRequest request) {
        consumeTokenRate("CLIENT_INVITATION_PREVIEW", rawToken, request);
        ClientInvitation invitation=invitations.findByTokenHash(CryptoSupport.sha256Hex(rawToken)).orElseThrow(ClientInvitationService::invalidToken);
        if(!invitation.usable(clock.instant())) throw invalidToken();
        OnboardingInstance onboarding = onboardings.findByOrganizationIdAndId(invitation.getOrganizationId(), invitation.getOnboardingId())
                .orElseThrow(ClientInvitationService::invalidToken);
        if (!INVITABLE.contains(onboarding.getStatus())) throw invalidToken();
        var project=projects.require(invitation.getOrganizationId(), invitation.getProjectId());
        var org=organizations.findOrganizationById(invitation.getOrganizationId()).orElseThrow(ClientInvitationService::invalidToken);
        boolean existing=users.findByNormalizedEmail(invitation.getNormalizedEmail()).isPresent();
        return new ClientInvitationPreviewResponse(invitation.getId(),org.getName(),org.getSlug(),project.name(),
                invitation.getDisplayNameSnapshot(),invitation.getEmail(),invitation.getAccessLevel(),invitation.getExpiresAt(),existing);
    }

    public AuthResponse accept(AcceptClientInvitationRequest body, HttpServletRequest request, HttpServletResponse response) {
        consumeTokenRate("CLIENT_INVITATION_ACCEPT", body.token(), request);
        AcceptResult result=transactions.execute(status->acceptInTransaction(body,request));
        if(result==null) throw new IllegalStateException("Invitation acceptance returned no result");
        return clientAuth.authenticateAfterInvitation(result.organizationId(),result.user(),request,response);
    }

    @Transactional
    protected AcceptResult acceptInTransaction(AcceptClientInvitationRequest body, HttpServletRequest request) {
        Instant now=clock.instant();
        ClientInvitation invitation=invitations.findByTokenHashForUpdate(CryptoSupport.sha256Hex(body.token())).orElseThrow(ClientInvitationService::invalidToken);
        if(!invitation.usable(now)) throw invalidToken();
        OnboardingInstance onboarding=requireForUpdate(invitation.getOrganizationId(),invitation.getOnboardingId());
        if(Set.of(OnboardingStatus.CANCELLED,OnboardingStatus.EXPIRED,OnboardingStatus.COMPLETED,OnboardingStatus.APPROVED).contains(onboarding.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT,"ONBOARDING_NOT_ACCEPTING_INVITATIONS","This onboarding is no longer accepting client access.");
        }
        var project=projects.require(invitation.getOrganizationId(),invitation.getProjectId());
        var activated=activation.activate(invitation.getOrganizationId(),invitation.getClientId(),invitation.getProjectId(),
                invitation.getEmail(),invitation.getDisplayNameSnapshot(),invitation.getAccessLevel(),invitation.getInvitedBy(),body.password());
        invitation.accept(activated.user().getId(),now);
        invitations.saveAndFlush(invitation);
        OnboardingStatus before=onboarding.getStatus();
        if(before==OnboardingStatus.DRAFT) onboarding.transitionTo(OnboardingStatus.INVITED,activated.user().getId(),now);
        if(onboarding.getStatus()==OnboardingStatus.INVITED) {
            onboarding.transitionTo(OnboardingStatus.IN_PROGRESS,activated.user().getId(),now);
            onboarding.updateReadiness(onboarding.isReady(),activated.user().getId(),now);
        }
        onboardings.saveAndFlush(onboarding);
        if (before != OnboardingStatus.AWAITING_INTERNAL_REVIEW
                && onboarding.getStatus() == OnboardingStatus.AWAITING_INTERNAL_REVIEW) {
            activity.recordSystem(invitation.getOrganizationId(), invitation.getClientId(), invitation.getProjectId(),
                    "ONBOARDING_READY_FOR_REVIEW", "ONBOARDING", onboarding.getId(),
                    "All blocking onboarding requirements are complete", Map.of("ready", true));
            outbox.record(invitation.getOrganizationId(), "ONBOARDING_READY_FOR_REVIEW", "ONBOARDING", onboarding.getId(),
                    Map.of("onboardingId", onboarding.getId(), "projectId", invitation.getProjectId()));
        }
        activity.recordClient(invitation.getOrganizationId(),invitation.getClientId(),invitation.getProjectId(),activated.user().getId(),
                "CLIENT_INVITATION_ACCEPTED","CLIENT_INVITATION",invitation.getId(),"Client accepted portal invitation",Map.of());
        audit.recordClient(invitation.getOrganizationId(),activated.user().getId(),"CLIENT_INVITATION_ACCEPTED","CLIENT_INVITATION",invitation.getId(),null,
                Map.of("projectId",project.id(),"onboardingId",onboarding.getId(),"newIdentity",activated.newIdentity()),request);
        outbox.record(invitation.getOrganizationId(),"CLIENT_INVITATION_ACCEPTED","CLIENT_INVITATION",invitation.getId(),
                Map.of("onboardingId",onboarding.getId(),"projectId",project.id(),"userId",activated.user().getId()));
        return new AcceptResult(invitation.getOrganizationId(),activated.user());
    }

    private void deliver(InvitationDispatch dispatch, UUID organizationId, UUID actorId, HttpServletRequest request) {
        try { mail.sendClientInvitation(dispatch.email(),dispatch.organizationName(),dispatch.projectName(),dispatch.rawToken()); }
        catch(MailException ex){
            log.error("Client invitation email delivery failed for invitation {}",dispatch.response().id(),ex);
            audit.record(organizationId,actorId,"CLIENT_INVITATION_DELIVERY_FAILED","CLIENT_INVITATION",dispatch.response().id(),null,
                    Map.of("retryAvailable",true),request);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"INVITATION_DELIVERY_FAILED",
                    "The invitation was created, but email delivery failed. You can safely resend it.");
        }
    }

    private void consumeTokenRate(String scope,String raw,HttpServletRequest request){
        String ip=NetworkSource.clientIp(request);
        rateLimiter.consume(scope,CryptoSupport.sha256Hex(raw)+"|"+(ip==null?"unknown":ip));
    }
    private OnboardingInstance require(UUID org,UUID id){return onboardings.findByOrganizationIdAndId(org,id).orElseThrow(ClientInvitationService::notFound);}
    private OnboardingInstance requireForUpdate(UUID org,UUID id){return onboardings.findForUpdate(org,id).orElseThrow(ClientInvitationService::notFound);}
    private static void requireInvitable(OnboardingInstance value){if(!INVITABLE.contains(value.getStatus())) throw invalidState("This onboarding is not accepting new invitations.");}
    private static void requireVersion(ClientInvitation value,long expected){if(value.getVersion()!=expected) throw new ApiException(HttpStatus.CONFLICT,"VERSION_CONFLICT","The invitation changed. Refresh and try again.");}
    private static ClientInvitationResponse map(ClientInvitation i){return new ClientInvitationResponse(i.getId(),i.getOnboardingId(),i.getProjectId(),i.getClientId(),i.getContactId(),i.getEmail(),i.getDisplayNameSnapshot(),i.getAccessLevel(),i.getStatus(),i.getExpiresAt(),i.getLastSentAt(),i.getResendCount(),i.getAcceptedAt(),i.getRevokedAt(),i.getCreatedAt(),i.getVersion());}
    private static ApiException notFound(){return new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","Requested invitation resource was not found.");}
    private static ApiException invalidToken(){return new ApiException(HttpStatus.BAD_REQUEST,"INVITATION_INVALID","This invitation link is invalid, expired, revoked, or already used.");}
    private static ApiException invalidState(String message){return new ApiException(HttpStatus.CONFLICT,"CLIENT_INVITATION_STATE_INVALID",message);}

    private record InvitationDispatch(String rawToken,String email,String organizationName,String projectName,ClientInvitationResponse response){}
    private record AcceptResult(UUID organizationId,com.brainserve.onboarding.identity.domain.model.UserAccount user){}
}
