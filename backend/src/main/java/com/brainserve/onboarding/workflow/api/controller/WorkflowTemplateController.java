package com.brainserve.onboarding.workflow.api.controller;

import com.brainserve.onboarding.common.api.ApiResponse;
import com.brainserve.onboarding.common.observability.RequestContext;
import com.brainserve.onboarding.common.security.TenantPrincipal;
import com.brainserve.onboarding.workflow.api.request.CreateWorkflowTemplateRequest;
import com.brainserve.onboarding.workflow.api.request.CreateWorkflowVersionRequest;
import com.brainserve.onboarding.workflow.api.request.SaveWorkflowDraftRequest;
import com.brainserve.onboarding.workflow.api.request.UpdateWorkflowTemplateRequest;
import com.brainserve.onboarding.workflow.api.request.WorkflowVersionRequest;
import com.brainserve.onboarding.workflow.api.response.WorkflowTemplateDetailResponse;
import com.brainserve.onboarding.workflow.api.response.WorkflowTemplateSummaryResponse;
import com.brainserve.onboarding.workflow.api.response.WorkflowVersionDetailResponse;
import com.brainserve.onboarding.workflow.application.service.WorkflowTemplateService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/workflow-templates")
public class WorkflowTemplateController {
    private final WorkflowTemplateService service;

    public WorkflowTemplateController(WorkflowTemplateService service) { this.service = service; }

    @PostMapping
    @PreAuthorize("hasAuthority('WORKFLOW_MANAGE')")
    ApiResponse<WorkflowTemplateDetailResponse> create(@AuthenticationPrincipal TenantPrincipal principal,
                                                       @Valid @RequestBody CreateWorkflowTemplateRequest body,
                                                       HttpServletRequest request) {
        return ApiResponse.success(service.create(principal, body, request), RequestContext.requestId());
    }

    @GetMapping
    @PreAuthorize("hasAuthority('WORKFLOW_READ') or hasAuthority('WORKFLOW_MANAGE')")
    ApiResponse<java.util.List<WorkflowTemplateSummaryResponse>> list(@AuthenticationPrincipal TenantPrincipal principal,
                                                                       @RequestParam(defaultValue = "0") @Min(0) int page,
                                                                       @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size,
                                                                       @RequestParam(defaultValue = "false") boolean includeArchived) {
        var result = service.list(principal, page, size, includeArchived);
        return ApiResponse.success(result.items(), meta(result.page(), result.size(), result.totalElements(), result.totalPages()), RequestContext.requestId());
    }

    @GetMapping("/{templateId}")
    @PreAuthorize("hasAuthority('WORKFLOW_READ') or hasAuthority('WORKFLOW_MANAGE')")
    ApiResponse<WorkflowTemplateDetailResponse> get(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID templateId) {
        return ApiResponse.success(service.get(principal, templateId), RequestContext.requestId());
    }

    @PatchMapping("/{templateId}")
    @PreAuthorize("hasAuthority('WORKFLOW_MANAGE')")
    ApiResponse<WorkflowTemplateDetailResponse> update(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID templateId,
                                                       @Valid @RequestBody UpdateWorkflowTemplateRequest body,
                                                       HttpServletRequest request) {
        return ApiResponse.success(service.update(principal, templateId, body, request), RequestContext.requestId());
    }

    @PostMapping("/{templateId}/archive")
    @PreAuthorize("hasAuthority('WORKFLOW_MANAGE')")
    ApiResponse<WorkflowTemplateDetailResponse> archive(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID templateId,
                                                        @Valid @RequestBody WorkflowVersionRequest body,
                                                        HttpServletRequest request) {
        return ApiResponse.success(service.archive(principal, templateId, body.version(), request), RequestContext.requestId());
    }

    @PostMapping("/{templateId}/versions")
    @PreAuthorize("hasAuthority('WORKFLOW_MANAGE')")
    ApiResponse<WorkflowVersionDetailResponse> createVersion(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID templateId,
                                                             @Valid @RequestBody CreateWorkflowVersionRequest body,
                                                             HttpServletRequest request) {
        return ApiResponse.success(service.createVersion(principal, templateId, body, request), RequestContext.requestId());
    }

    @GetMapping("/versions/{versionId}")
    @PreAuthorize("hasAuthority('WORKFLOW_READ') or hasAuthority('WORKFLOW_MANAGE')")
    ApiResponse<WorkflowVersionDetailResponse> version(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID versionId) {
        return ApiResponse.success(service.getVersion(principal, versionId), RequestContext.requestId());
    }

    @PutMapping("/versions/{versionId}")
    @PreAuthorize("hasAuthority('WORKFLOW_MANAGE')")
    ApiResponse<WorkflowVersionDetailResponse> saveDraft(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID versionId,
                                                         @Valid @RequestBody SaveWorkflowDraftRequest body,
                                                         HttpServletRequest request) {
        return ApiResponse.success(service.saveDraft(principal, versionId, body, request), RequestContext.requestId());
    }

    @PostMapping("/versions/{versionId}/publish")
    @PreAuthorize("hasAuthority('WORKFLOW_MANAGE')")
    ApiResponse<WorkflowVersionDetailResponse> publish(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID versionId,
                                                       @Valid @RequestBody WorkflowVersionRequest body,
                                                       HttpServletRequest request) {
        return ApiResponse.success(service.publish(principal, versionId, body.version(), request), RequestContext.requestId());
    }

    private static Map<String, Object> meta(int page, int size, long totalElements, int totalPages) {
        return Map.of("page", page, "size", size, "totalElements", totalElements, "totalPages", totalPages);
    }
}
