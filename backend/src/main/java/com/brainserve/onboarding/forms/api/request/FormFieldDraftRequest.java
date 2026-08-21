package com.brainserve.onboarding.forms.api.request;
import com.brainserve.onboarding.forms.domain.model.FormFieldType; import com.fasterxml.jackson.databind.JsonNode; import jakarta.validation.constraints.*;
public record FormFieldDraftRequest(@NotBlank @Size(max=80) String fieldKey,@NotBlank @Size(max=220) String label,@Size(max=1000) String helpText,@NotNull FormFieldType fieldType,@PositiveOrZero int displayOrder,boolean required,JsonNode conditionExpression,JsonNode configuration){}
