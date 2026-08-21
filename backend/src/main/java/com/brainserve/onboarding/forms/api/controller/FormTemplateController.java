package com.brainserve.onboarding.forms.api.controller;

import com.brainserve.onboarding.common.api.ApiResponse;
import com.brainserve.onboarding.common.observability.RequestContext;
import com.brainserve.onboarding.common.security.TenantPrincipal;
import com.brainserve.onboarding.forms.api.request.CreateFormRequest;
import com.brainserve.onboarding.forms.api.request.CreateFormVersionRequest;
import com.brainserve.onboarding.forms.api.request.FormVersionRequest;
import com.brainserve.onboarding.forms.api.request.SaveFormDraftRequest;
import com.brainserve.onboarding.forms.api.request.UpdateFormRequest;
import com.brainserve.onboarding.forms.api.response.FormDetailResponse;
import com.brainserve.onboarding.forms.api.response.FormSummaryResponse;
import com.brainserve.onboarding.forms.api.response.FormVersionDetailResponse;
import com.brainserve.onboarding.forms.api.response.PublishedFormVersionResponse;
import com.brainserve.onboarding.forms.application.service.FormTemplateService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/forms")
public class FormTemplateController {
    private final FormTemplateService service;

    public FormTemplateController(FormTemplateService service) { this.service = service; }

    @PostMapping
    @PreAuthorize("hasAuthority('FORM_MANAGE')")
    ApiResponse<FormDetailResponse> create(@AuthenticationPrincipal TenantPrincipal principal,
                                           @Valid @RequestBody CreateFormRequest body,
                                           HttpServletRequest request) {
        return ApiResponse.success(service.create(principal, body, request), RequestContext.requestId());
    }

    @GetMapping
    @PreAuthorize("hasAuthority('FORM_READ') or hasAuthority('FORM_MANAGE') or hasAuthority('FORM_REVIEW')")
    ApiResponse<List<FormSummaryResponse>> list(@AuthenticationPrincipal TenantPrincipal principal,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "50") int size,
                                                @RequestParam(defaultValue = "false") boolean includeArchived) {
        var result = service.list(principal, page, size, includeArchived);
        return ApiResponse.success(result.items(), meta(result.page(), result.size(), result.totalElements(), result.totalPages()), RequestContext.requestId());
    }

    @GetMapping("/published-versions")
    @PreAuthorize("hasAuthority('FORM_READ') or hasAuthority('FORM_MANAGE')")
    ApiResponse<List<PublishedFormVersionResponse>> publishedVersions(@AuthenticationPrincipal TenantPrincipal principal) {
        return ApiResponse.success(service.publishedVersions(principal), RequestContext.requestId());
    }

    @GetMapping("/{formId}")
    @PreAuthorize("hasAuthority('FORM_READ') or hasAuthority('FORM_MANAGE') or hasAuthority('FORM_REVIEW')")
    ApiResponse<FormDetailResponse> get(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID formId) {
        return ApiResponse.success(service.get(principal, formId), RequestContext.requestId());
    }

    @PatchMapping("/{formId}")
    @PreAuthorize("hasAuthority('FORM_MANAGE')")
    ApiResponse<FormDetailResponse> update(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID formId,
                                           @Valid @RequestBody UpdateFormRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.update(principal, formId, body, request), RequestContext.requestId());
    }

    @PostMapping("/{formId}/archive")
    @PreAuthorize("hasAuthority('FORM_MANAGE')")
    ApiResponse<FormDetailResponse> archive(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID formId,
                                            @Valid @RequestBody FormVersionRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.archive(principal, formId, body.version(), request), RequestContext.requestId());
    }

    @PostMapping("/{formId}/versions")
    @PreAuthorize("hasAuthority('FORM_MANAGE')")
    ApiResponse<FormVersionDetailResponse> createVersion(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID formId,
                                                         @Valid @RequestBody CreateFormVersionRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.createVersion(principal, formId, body, request), RequestContext.requestId());
    }

    @GetMapping("/versions/{versionId}")
    @PreAuthorize("hasAuthority('FORM_READ') or hasAuthority('FORM_MANAGE') or hasAuthority('FORM_REVIEW')")
    ApiResponse<FormVersionDetailResponse> version(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID versionId) {
        return ApiResponse.success(service.getVersion(principal, versionId), RequestContext.requestId());
    }

    @PutMapping("/versions/{versionId}")
    @PreAuthorize("hasAuthority('FORM_MANAGE')")
    ApiResponse<FormVersionDetailResponse> saveDraft(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID versionId,
                                                      @Valid @RequestBody SaveFormDraftRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.saveDraft(principal, versionId, body, request), RequestContext.requestId());
    }

    @PostMapping("/versions/{versionId}/publish")
    @PreAuthorize("hasAuthority('FORM_MANAGE')")
    ApiResponse<FormVersionDetailResponse> publish(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID versionId,
                                                   @Valid @RequestBody FormVersionRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.publish(principal, versionId, body.version(), request), RequestContext.requestId());
    }

    private static Map<String, Object> meta(int page, int size, long totalElements, int totalPages) {
        return Map.of("page", page, "size", size, "totalElements", totalElements, "totalPages", totalPages);
    }
}
