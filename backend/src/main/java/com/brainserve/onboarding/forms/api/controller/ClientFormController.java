package com.brainserve.onboarding.forms.api.controller;

import com.brainserve.onboarding.common.api.ApiResponse;
import com.brainserve.onboarding.common.observability.RequestContext;
import com.brainserve.onboarding.common.security.ClientPrincipal;
import com.brainserve.onboarding.forms.api.request.SaveFormResponseDraftRequest;
import com.brainserve.onboarding.forms.api.request.SubmitFormResponseRequest;
import com.brainserve.onboarding.forms.api.response.ClientFormStepResponse;
import com.brainserve.onboarding.forms.application.service.FormSubmissionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/client-portal/projects/{projectId}/forms")
@PreAuthorize("hasAuthority('CLIENT_PORTAL')")
public class ClientFormController {
    private final FormSubmissionService service;
    public ClientFormController(FormSubmissionService service) { this.service = service; }

    @GetMapping("/{stepId}")
    ApiResponse<ClientFormStepResponse> get(@AuthenticationPrincipal ClientPrincipal principal,
                                            @PathVariable UUID projectId, @PathVariable UUID stepId) {
        return ApiResponse.success(service.clientStep(principal, projectId, stepId), RequestContext.requestId());
    }

    @PutMapping("/{stepId}/draft")
    ApiResponse<ClientFormStepResponse> saveDraft(@AuthenticationPrincipal ClientPrincipal principal,
                                                  @PathVariable UUID projectId, @PathVariable UUID stepId,
                                                  @Valid @RequestBody SaveFormResponseDraftRequest body,
                                                  HttpServletRequest request) {
        return ApiResponse.success(service.saveDraft(principal, projectId, stepId, body, request), RequestContext.requestId());
    }

    @PostMapping("/{stepId}/submit")
    ApiResponse<ClientFormStepResponse> submit(@AuthenticationPrincipal ClientPrincipal principal,
                                               @PathVariable UUID projectId, @PathVariable UUID stepId,
                                               @Valid @RequestBody SubmitFormResponseRequest body,
                                               HttpServletRequest request) {
        return ApiResponse.success(service.submit(principal, projectId, stepId, body, request), RequestContext.requestId());
    }
}
