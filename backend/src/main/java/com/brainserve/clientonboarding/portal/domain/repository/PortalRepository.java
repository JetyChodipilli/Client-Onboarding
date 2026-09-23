package com.brainserve.clientonboarding.portal.domain.repository;

import com.brainserve.clientonboarding.auth.application.ClientSessionAccessPort.ClientSessionAccess;
import com.brainserve.clientonboarding.portal.domain.model.ClientInvitation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PortalRepository {
    Optional<InvitationTarget> findInvitationTarget(UUID organizationId, UUID onboardingId, UUID contactId);
    Optional<ClientInvitation> findInvitation(UUID organizationId, UUID invitationId);
    Optional<ClientInvitation> findInvitationByTokenHash(String tokenHash);
    List<ClientInvitation> findInvitations(UUID organizationId, UUID onboardingId, int page, int size);
    void insertInvitation(ClientInvitation invitation, UUID actorId);
    boolean rotateInvitation(UUID organizationId, UUID invitationId, String tokenHash, Instant expiresAt,
                             long version, UUID actorId, Instant now);
    boolean revokeInvitation(UUID organizationId, UUID invitationId, long version, UUID actorId, Instant now);
    void markDelivery(UUID organizationId, UUID invitationId, ClientInvitation.DeliveryStatus status,
                      String error, UUID actorId, Instant now);
    Optional<ClientUserLink> findClientUserLink(UUID organizationId, UUID userId);
    UUID activateClientUser(ClientInvitation invitation, UUID userId, UUID actorId, Instant now);
    void grantProjectAccess(UUID organizationId, UUID clientId, UUID clientUserId, UUID projectId,
                            UUID actorId, Instant now);
    boolean acceptInvitation(UUID organizationId, UUID invitationId, long version, UUID userId,
                             UUID actorId, Instant now);
    Optional<ClientSessionAccess> findClientAccess(UUID userId, UUID organizationId);
    Optional<ClientSessionAccess> findClientAccess(String normalizedEmail, String normalizedSlug);
    List<PortalProject> findPortalProjects(UUID organizationId, UUID clientUserId, int page, int size);
    Optional<PortalProject> findPortalProject(UUID organizationId, UUID clientUserId, UUID projectId);
    Optional<String> findHelpEmail(UUID organizationId, UUID projectId);

    record InvitationTarget(UUID organizationId, String organizationName, String organizationSlug,
                            UUID onboardingId, String onboardingStatus, long onboardingVersion,
                            UUID projectId, String projectName, UUID clientId, String clientName,
                            UUID contactId, String contactName, String contactEmail) { }

    record PortalProject(UUID projectId, String projectName, String projectStatus, String clientName,
                         UUID onboardingId, String onboardingStatus, boolean ready) { }
    record ClientUserLink(UUID id, UUID clientId, String status) { }
}
