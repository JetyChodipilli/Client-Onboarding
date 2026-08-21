package com.brainserve.onboarding.contracts.api.request;
import jakarta.validation.constraints.*;
public record UpdateContractTemplateVersionRequest(@NotBlank @Size(max=240) String title,@NotBlank @Size(max=200000) String legalContent,@PositiveOrZero long version){}
