package com.brainserve.onboarding.contracts.api.request;
import jakarta.validation.constraints.*;
public record CreateContractTemplateRequest(@NotBlank @Size(max=180) String name,@Size(max=2000) String description,@NotBlank @Size(max=240) String title,@NotBlank @Size(max=200000) String legalContent){}
