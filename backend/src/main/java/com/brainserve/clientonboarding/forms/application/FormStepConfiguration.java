package com.brainserve.clientonboarding.forms.application;

import com.brainserve.clientonboarding.forms.domain.model.FormModels.*;
import com.brainserve.clientonboarding.forms.domain.repository.FormRepository;
import com.brainserve.clientonboarding.workflow.application.StepConfigurationValidator;
import com.brainserve.clientonboarding.workflow.domain.model.TemplateStep;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class FormStepConfiguration implements StepConfigurationValidator {
    private final FormRepository repository;
    public FormStepConfiguration(FormRepository repository) { this.repository=repository; }
    @Override
    public void validate(UUID org,TemplateStep step) {
        if(step.stepType()!=TemplateStep.StepType.FORM) return;
        Definition definition=require(org,step.configuration());
        if(!repository.lockTemplate(org,definition.formId())) throw FormErrors.missing();
        if(!step.clientVisible()) throw FormErrors.invalid("Questionnaire steps must be client visible.");
    }
    public Definition require(UUID org,Map<String,Object> configuration) {
        Object value=configuration.get("formVersionId");
        UUID id;
        try { id=UUID.fromString(value instanceof String s?s:""); }
        catch(IllegalArgumentException e) { throw FormErrors.state("FORM_VERSION_REQUIRED","Select a published form version for this workflow step."); }
        return repository.definition(org,id).filter(d -> d.status()==VersionStatus.PUBLISHED).orElseThrow(FormErrors::missing);
    }
}
