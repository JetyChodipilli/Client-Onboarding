package com.brainserve.onboarding.project.application.service;

import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.project.domain.model.Project;
import com.brainserve.onboarding.project.domain.model.ProjectStatus;
import com.brainserve.onboarding.project.infrastructure.persistence.ProjectRepository;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only project boundary for the external client portal. */
@Service
public class ProjectPortalQueryService {
    private final ProjectRepository projects;
    public ProjectPortalQueryService(ProjectRepository projects){this.projects=projects;}

    @Transactional(readOnly=true)
    public List<ProjectRef> findAll(UUID organizationId, Collection<UUID> projectIds){
        if(projectIds.isEmpty()) return List.of();
        return projects.findAllByOrganizationIdAndIdIn(organizationId,projectIds).stream().map(ProjectPortalQueryService::map).toList();
    }

    @Transactional(readOnly=true)
    public ProjectRef require(UUID organizationId, UUID projectId){
        return map(projects.findByOrganizationIdAndId(organizationId,projectId)
                .orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","Requested project was not found.")));
    }

    private static ProjectRef map(Project p){return new ProjectRef(p.getId(),p.getClientId(),p.getServiceId(),p.getName(),p.getStatus());}
    public record ProjectRef(UUID id,UUID clientId,UUID serviceId,String name,ProjectStatus status){}
}
