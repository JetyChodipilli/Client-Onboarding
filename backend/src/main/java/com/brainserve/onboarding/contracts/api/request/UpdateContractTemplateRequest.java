package com.brainserve.onboarding.contracts.api.request;
import jakarta.validation.constraints.*;
public record UpdateContractTemplateRequest(@NotBlank @Size(max=180) String name,@Size(max=2000) String description,@PositiveOrZero long version){}
