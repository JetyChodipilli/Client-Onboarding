package com.brainserve.onboarding.contracts.api.request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
public record ContractActionRequest(@PositiveOrZero long version,@NotBlank @Size(max=1000) String reason){}
