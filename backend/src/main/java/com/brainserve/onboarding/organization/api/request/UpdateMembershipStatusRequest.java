package com.brainserve.onboarding.organization.api.request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
public record UpdateMembershipStatusRequest(@NotBlank @Pattern(regexp="ACTIVE|SUSPENDED") String status,
                                            @PositiveOrZero long version) {}
