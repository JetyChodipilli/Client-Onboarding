package com.brainserve.onboarding.project.api.controller;

import com.brainserve.onboarding.common.api.ActivityResponse;
import com.brainserve.onboarding.common.api.ApiResponse;
import com.brainserve.onboarding.common.observability.RequestContext;
import com.brainserve.onboarding.common.security.TenantPrincipal;
import com.brainserve.onboarding.project.api.request.AddProjectMemberRequest;
import com.brainserve.onboarding.project.api.request.CreateProjectRequest;
import com.brainserve.onboarding.project.api.request.ProjectVersionRequest;
import com.brainserve.onboarding.project.api.request.UpdateProjectRequest;
import com.brainserve.onboarding.project.api.response.ProjectActivationResponse;
import com.brainserve.onboarding.project.api.response.ProjectMemberResponse;
import com.brainserve.onboarding.project.api.response.ProjectResponse;
import com.brainserve.onboarding.project.application.service.ProjectActivationService;
import com.brainserve.onboarding.project.application.service.ProjectService;
import com.brainserve.onboarding.project.domain.model.ProjectStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {
    private final ProjectService service;
    private final ProjectActivationService activation;

    public ProjectController(ProjectService service, ProjectActivationService activation) {
        this.service = service;
        this.activation = activation;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PROJECT_CREATE')")
    ApiResponse<ProjectResponse> create(@AuthenticationPrincipal TenantPrincipal principal,
                                        @Valid @RequestBody CreateProjectRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.create(principal, body, request), RequestContext.requestId());
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PROJECT_READ')")
    ApiResponse<List<ProjectResponse>> list(@AuthenticationPrincipal TenantPrincipal principal,
                                            @RequestParam(defaultValue = "0") @Min(0) int page,
                                            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size,
                                            @RequestParam(required = false) ProjectStatus status,
                                            @RequestParam(required = false) UUID clientId,
                                            @RequestParam(required = false) @Size(max = 120) String query,
                                            @RequestParam(defaultValue = "false") boolean includeArchived) {
        var result = service.list(principal, page, size, status, clientId, query, includeArchived);
        return ApiResponse.success(result.items(), meta(result.page(), result.size(), result.totalElements(), result.totalPages()), RequestContext.requestId());
    }

    @GetMapping("/{projectId}")
    @PreAuthorize("hasAuthority('PROJECT_READ')")
    ApiResponse<ProjectResponse> get(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID projectId) {
        return ApiResponse.success(service.get(principal, projectId), RequestContext.requestId());
    }

    @PatchMapping("/{projectId}")
    @PreAuthorize("hasAuthority('PROJECT_UPDATE')")
    ApiResponse<ProjectResponse> update(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID projectId,
                                        @Valid @RequestBody UpdateProjectRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.update(principal, projectId, body, request), RequestContext.requestId());
    }

    @PostMapping("/{projectId}/cancel")
    @PreAuthorize("hasAuthority('PROJECT_UPDATE')")
    ApiResponse<ProjectResponse> cancel(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID projectId,
                                        @Valid @RequestBody ProjectVersionRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.cancel(principal, projectId, body, request), RequestContext.requestId());
    }

    @PostMapping("/{projectId}/activate")
    @PreAuthorize("hasAuthority('PROJECT_ACTIVATE')")
    ApiResponse<ProjectActivationResponse> activate(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID projectId,
                                                     @Valid @RequestBody ProjectVersionRequest body, HttpServletRequest request) {
        var result = activation.activate(principal, projectId, body.version(), request);
        return ApiResponse.success(new ProjectActivationResponse(result.id(), result.status(), result.version()), RequestContext.requestId());
    }

    @PostMapping("/{projectId}/hold")
    @PreAuthorize("hasAuthority('PROJECT_UPDATE')")
    ApiResponse<ProjectResponse> hold(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID projectId,
                                      @Valid @RequestBody ProjectVersionRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.hold(principal, projectId, body, request), RequestContext.requestId());
    }

    @PostMapping("/{projectId}/resume")
    @PreAuthorize("hasAuthority('PROJECT_UPDATE')")
    ApiResponse<ProjectResponse> resume(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID projectId,
                                        @Valid @RequestBody ProjectVersionRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.resume(principal, projectId, body, request), RequestContext.requestId());
    }

    @PostMapping("/{projectId}/archive")
    @PreAuthorize("hasAuthority('PROJECT_UPDATE')")
    ApiResponse<ProjectResponse> archive(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID projectId,
                                         @Valid @RequestBody ProjectVersionRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.archive(principal, projectId, body, request), RequestContext.requestId());
    }

    @GetMapping("/{projectId}/members")
    @PreAuthorize("hasAuthority('PROJECT_READ')")
    ApiResponse<List<ProjectMemberResponse>> members(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID projectId) {
        return ApiResponse.success(service.listMembers(principal, projectId), RequestContext.requestId());
    }

    @PostMapping("/{projectId}/members")
    @PreAuthorize("hasAuthority('PROJECT_UPDATE')")
    ApiResponse<ProjectMemberResponse> addMember(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID projectId,
                                                 @Valid @RequestBody AddProjectMemberRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.addMember(principal, projectId, body, request), RequestContext.requestId());
    }

    @DeleteMapping("/{projectId}/members/{memberId}")
    @PreAuthorize("hasAuthority('PROJECT_UPDATE')")
    ApiResponse<Void> removeMember(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID projectId,
                                   @PathVariable UUID memberId, HttpServletRequest request) {
        service.removeMember(principal, projectId, memberId, request);
        return ApiResponse.success(null, RequestContext.requestId());
    }

    @GetMapping("/{projectId}/activity")
    @PreAuthorize("hasAuthority('PROJECT_READ')")
    ApiResponse<List<ActivityResponse>> activity(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID projectId,
                                                        @RequestParam(defaultValue = "0") @Min(0) int page,
                                                        @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        var result = service.activity(principal, projectId, page, size);
        return ApiResponse.success(result.items(), meta(result.page(), result.size(), result.totalElements(), result.totalPages()), RequestContext.requestId());
    }

    private static Map<String, Object> meta(int page, int size, long totalElements, int totalPages) {
        return Map.of("page", page, "size", size, "totalElements", totalElements, "totalPages", totalPages);
    }
}
