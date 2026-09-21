package com.brainserve.clientonboarding.onboarding.api;

import com.brainserve.clientonboarding.common.api.ApiSuccess;
import com.brainserve.clientonboarding.common.observability.RequestIds;
import com.brainserve.clientonboarding.common.observability.RequestMetadata;
import com.brainserve.clientonboarding.common.security.CurrentPrincipal;
import com.brainserve.clientonboarding.onboarding.application.OnboardingService;
import com.brainserve.clientonboarding.onboarding.domain.model.OnboardingStepInstance;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class OnboardingController {
    private final OnboardingService service;
    public OnboardingController(OnboardingService service) { this.service = service; }

    @PostMapping("/projects/{projectId}/onboarding")
    @ResponseStatus(HttpStatus.CREATED)
    ApiSuccess<OnboardingService.OnboardingView> start(@PathVariable UUID projectId,
                                                       @Valid @RequestBody StartRequest request,
                                                       @RequestHeader(value = "Idempotency-Key", required = false)
                                                       String idempotencyKey,
                                                       Authentication authentication,
                                                       HttpServletRequest servletRequest) {
        return ApiSuccess.of(service.start(CurrentPrincipal.require(authentication), projectId,
                new OnboardingService.StartCommand(request.templateVersionId(), request.projectVersion()),
                idempotencyKey, RequestMetadata.from(servletRequest)), RequestIds.currentRequestId());
    }

    @GetMapping("/onboardings/{onboardingId}")
    ApiSuccess<OnboardingService.OnboardingView> get(@PathVariable UUID onboardingId,
                                                     Authentication authentication) {
        return ApiSuccess.of(service.get(CurrentPrincipal.require(authentication), onboardingId),
                RequestIds.currentRequestId());
    }

    @GetMapping("/projects/{projectId}/onboarding")
    ApiSuccess<OnboardingService.OnboardingView> getByProject(@PathVariable UUID projectId,
                                                              Authentication authentication) {
        return ApiSuccess.of(service.getByProject(CurrentPrincipal.require(authentication), projectId),
                RequestIds.currentRequestId());
    }

    @PostMapping("/onboarding-steps/{stepId}/transition")
    ApiSuccess<OnboardingService.OnboardingView> transition(@PathVariable UUID stepId,
                                                            @Valid @RequestBody StepRequest request,
                                                            Authentication authentication,
                                                            HttpServletRequest servletRequest) {
        return ApiSuccess.of(service.transitionStep(CurrentPrincipal.require(authentication), stepId,
                new OnboardingService.StepCommand(request.targetStatus(), request.version()),
                RequestMetadata.from(servletRequest)), RequestIds.currentRequestId());
    }

    public record StartRequest(@NotNull UUID templateVersionId, @PositiveOrZero long projectVersion) { }
    public record StepRequest(@NotNull OnboardingStepInstance.Status targetStatus,
                              @PositiveOrZero long version) { }
}
