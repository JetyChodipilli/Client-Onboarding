package com.brainserve.onboarding.organization.api.request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;
public record CreateRoleRequest(@NotBlank @Size(max=80) String code,
                                @NotBlank @Size(max=120) String name,
                                @Size(max=240) String description,
                                @NotEmpty Set<UUID> permissionIds) { public CreateRoleRequest { permissionIds = permissionIds == null ? Set.of() : Set.copyOf(permissionIds); } }
