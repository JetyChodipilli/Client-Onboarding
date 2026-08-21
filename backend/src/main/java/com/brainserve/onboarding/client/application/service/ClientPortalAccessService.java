package com.brainserve.onboarding.client.application.service;

import com.brainserve.onboarding.client.domain.model.ClientProjectAccessLevel;
import com.brainserve.onboarding.client.domain.model.ClientUser;
import com.brainserve.onboarding.client.domain.model.ClientUserProject;
import com.brainserve.onboarding.client.domain.model.ClientUserProjectStatus;
import com.brainserve.onboarding.client.domain.model.ClientUserStatus;
import com.brainserve.onboarding.client.infrastructure.persistence.ClientUserProjectRepository;
import com.brainserve.onboarding.client.infrastructure.persistence.ClientUserRepository;
import com.brainserve.onboarding.common.error.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public client-module boundary for authenticated client identities and project authorization.
 * Sibling modules never access client-user persistence directly.
 */
@Service
public class ClientPortalAccessService {
    private final ClientUserRepository clientUsers;
    private final ClientUserProjectRepository projectAccess;

    public ClientPortalAccessService(ClientUserRepository clientUsers, ClientUserProjectRepository projectAccess) {
        this.clientUsers = clientUsers;
        this.projectAccess = projectAccess;
    }

    @Transactional
    public GrantResult grantProjectAccess(UUID organizationId, UUID clientId, UUID projectId, UUID userId,
                                          ClientProjectAccessLevel accessLevel, UUID actorId, Instant now) {
        ClientUser clientUser = clientUsers.findForUpdate(organizationId, clientId, userId).orElse(null);
        boolean createdUser = false;
        if (clientUser == null) {
            clientUser = clientUsers.saveAndFlush(new ClientUser(
                    UUID.randomUUID(), organizationId, clientId, userId, actorId, now));
            createdUser = true;
        } else if (clientUser.getStatus() != ClientUserStatus.ACTIVE) {
            if (clientUser.getStatus() == ClientUserStatus.SUSPENDED) {
                throw new ApiException(HttpStatus.CONFLICT, "CLIENT_ACCOUNT_SUSPENDED",
                        "This client account is suspended and cannot accept new project access.");
            }
            clientUser.reactivate(actorId, now);
            clientUsers.saveAndFlush(clientUser);
        }

        ClientUserProject access = projectAccess.findByOrganizationIdAndClientUserIdAndProjectId(
                organizationId, clientUser.getId(), projectId).orElse(null);
        boolean createdProjectAccess = false;
        if (access == null) {
            access = projectAccess.saveAndFlush(new ClientUserProject(
                    UUID.randomUUID(), organizationId, clientId, clientUser.getId(), projectId,
                    accessLevel, actorId, now));
            createdProjectAccess = true;
        } else if (access.getStatus() != ClientUserProjectStatus.ACTIVE || access.getAccessLevel() != accessLevel) {
            access.restore(accessLevel, actorId, now);
            projectAccess.saveAndFlush(access);
        }
        return new GrantResult(clientUser.getId(), access.getId(), createdUser, createdProjectAccess);
    }

    @Transactional(readOnly = true)
    public boolean hasActiveTenantAccess(UUID organizationId, UUID userId) {
        return clientUsers.existsByOrganizationIdAndUserIdAndStatus(organizationId, userId, ClientUserStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<ProjectAccess> activeProjectAccess(UUID organizationId, UUID userId) {
        List<ClientUser> users = clientUsers.findAllByOrganizationIdAndUserIdAndStatus(
                organizationId, userId, ClientUserStatus.ACTIVE);
        if (users.isEmpty()) return List.of();
        Set<UUID> activeIds = users.stream().map(ClientUser::getId).collect(Collectors.toSet());
        return projectAccess.findAllByOrganizationIdAndClientUserIdInAndStatus(
                        organizationId, activeIds, ClientUserProjectStatus.ACTIVE).stream()
                .map(value -> new ProjectAccess(value.getClientUserId(), value.getClientId(), value.getProjectId(), value.getAccessLevel()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectAccess requireProjectAccess(UUID organizationId, UUID userId, UUID projectId) {
        return activeProjectAccess(organizationId, userId).stream()
                .filter(access -> access.projectId().equals(projectId))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Requested project was not found."));
    }

    /**
     * Requires a client-side project grant that is permitted to perform project-wide onboarding actions.
     * CLIENT_MEMBER remains intentionally read-only until a later task-assignment phase can scope actions safely.
     */
    @Transactional(readOnly = true)
    public ProjectAccess requireProjectActionAccess(UUID organizationId, UUID userId, UUID projectId) {
        ProjectAccess access = requireProjectAccess(organizationId, userId, projectId);
        if (access.accessLevel() != ClientProjectAccessLevel.CLIENT_ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CLIENT_PROJECT_ACTION_FORBIDDEN",
                    "This client account does not have permission to perform project-wide actions.");
        }
        return access;
    }

    public record GrantResult(UUID clientUserId, UUID projectAccessId, boolean clientUserCreated, boolean projectAccessCreated) {}
    public record ProjectAccess(UUID clientUserId, UUID clientId, UUID projectId, ClientProjectAccessLevel accessLevel) {}
}
