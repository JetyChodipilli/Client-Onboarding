package com.brainserve.onboarding.contracts.api.request;
import jakarta.validation.constraints.PositiveOrZero;
public record ContractVersionRequest(@PositiveOrZero long version){}
