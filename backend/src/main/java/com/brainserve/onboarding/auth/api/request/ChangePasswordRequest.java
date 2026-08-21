package com.brainserve.onboarding.auth.api.request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record ChangePasswordRequest(@NotBlank @Size(max=128) String currentPassword,
                                    @NotBlank @Size(max=128) String newPassword) {}
