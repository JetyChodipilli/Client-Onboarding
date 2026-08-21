package com.brainserve.onboarding.organization.api.request;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;
public record InviteOrganizationUserRequest(@Email @NotBlank @Size(max=320) String email,
                                            @NotBlank @Size(max=160) String displayName,
                                            @NotEmpty Set<UUID> roleIds) { public InviteOrganizationUserRequest { roleIds = roleIds == null ? Set.of() : Set.copyOf(roleIds); } }
