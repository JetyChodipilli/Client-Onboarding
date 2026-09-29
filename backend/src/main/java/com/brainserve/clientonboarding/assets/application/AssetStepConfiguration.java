package com.brainserve.clientonboarding.assets.application;

import com.brainserve.clientonboarding.assets.domain.model.AssetModels.Requirement;
import com.brainserve.clientonboarding.assets.domain.repository.AssetRepository;
import com.brainserve.clientonboarding.workflow.application.StepConfigurationValidator;
import com.brainserve.clientonboarding.workflow.domain.model.TemplateStep;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AssetStepConfiguration implements StepConfigurationValidator {
    private final AssetRepository repository;
    public AssetStepConfiguration(AssetRepository repository) {this.repository=repository;}
    public void validate(UUID org,TemplateStep step) {
        if(step.stepType()!=TemplateStep.StepType.FILE_UPLOAD)return;
        var r=require(org,step.configuration());
        if(!repository.lockRequirement(org,r.id()))throw AssetErrors.missing();
        if(!step.clientVisible())throw AssetErrors.invalid("File upload steps must be client visible.");
    }
    public Requirement require(UUID org,Map<String,Object> configuration) {
        UUID id;
        try {id=UUID.fromString(configuration.get("assetRequirementId") instanceof String s?s:"");}
        catch(IllegalArgumentException e){throw AssetErrors.invalid("Select an asset requirement for this file upload step.");}
        return repository.requirement(org,id).orElseThrow(AssetErrors::missing);
    }
}
