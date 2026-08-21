package com.brainserve.onboarding.access.application.service;

import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.workflow.application.service.WorkflowStepConfigurationValidator;
import com.brainserve.onboarding.workflow.domain.model.WorkflowStepType;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class PlatformAccessWorkflowConfigurationValidator implements WorkflowStepConfigurationValidator {
    private final PlatformAccessCatalogService catalog;
    public PlatformAccessWorkflowConfigurationValidator(PlatformAccessCatalogService catalog){this.catalog=catalog;}
    @Override public boolean supports(WorkflowStepType stepType){return stepType==WorkflowStepType.PLATFORM_ACCESS;}
    @Override public void validate(UUID organizationId,String stepKey,JsonNode configuration){
        if(configuration==null||!configuration.isObject()) throw invalid(stepKey,"configuration is required");
        if(configuration.size()!=1||configuration.get("platformAccessTypeVersionId")==null||!configuration.get("platformAccessTypeVersionId").isTextual()) throw invalid(stepKey,"configuration must contain only platformAccessTypeVersionId");
        UUID versionId;
        try{versionId=UUID.fromString(configuration.get("platformAccessTypeVersionId").asText());}catch(IllegalArgumentException ex){throw invalid(stepKey,"platformAccessTypeVersionId must be a UUID");}
        catalog.requirePublished(organizationId,versionId);
    }
    private static ApiException invalid(String stepKey,String message){return new ApiException(HttpStatus.BAD_REQUEST,"WORKFLOW_PLATFORM_ACCESS_INVALID","Platform access step '"+stepKey+"' "+message+".");}
}
