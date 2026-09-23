package com.brainserve.clientonboarding.portal.api;

import com.brainserve.clientonboarding.auth.application.SessionCookieService;
import com.brainserve.clientonboarding.common.api.ApiSuccess;
import com.brainserve.clientonboarding.common.observability.RequestIds;
import com.brainserve.clientonboarding.common.observability.RequestMetadata;
import com.brainserve.clientonboarding.common.security.CurrentPrincipal;
import com.brainserve.clientonboarding.onboarding.domain.model.OnboardingStepInstance;
import com.brainserve.clientonboarding.portal.application.ClientPortalService;
import com.brainserve.clientonboarding.portal.domain.model.ClientInvitation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ClientPortalController {
    private final ClientPortalService service;
    private final SessionCookieService cookies;

    public ClientPortalController(ClientPortalService service, SessionCookieService cookies) {
        this.service = service; this.cookies = cookies;
    }

    @PostMapping("/onboardings/{onboardingId}/client-invitations")
    @ResponseStatus(HttpStatus.CREATED)
    ApiSuccess<ClientPortalService.InvitationView> invite(@PathVariable UUID onboardingId,
            @Valid @RequestBody InviteRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication, HttpServletRequest servletRequest) {
        return ApiSuccess.of(service.invite(CurrentPrincipal.require(authentication), onboardingId,
                new ClientPortalService.InviteCommand(request.contactId(), request.role()), idempotencyKey,
                RequestMetadata.from(servletRequest)), RequestIds.currentRequestId());
    }

    @GetMapping("/onboardings/{onboardingId}/client-invitations")
    ApiSuccess<List<ClientPortalService.InvitationView>> invitations(@PathVariable UUID onboardingId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size,
                                                                     Authentication authentication) {
        return ApiSuccess.of(service.invitations(CurrentPrincipal.require(authentication), onboardingId, page, size),
                RequestIds.currentRequestId());
    }

    @PostMapping("/client-invitations/{invitationId}/resend")
    ApiSuccess<ClientPortalService.InvitationView> resend(@PathVariable UUID invitationId,
            @Valid @RequestBody VersionRequest request, Authentication authentication,
            HttpServletRequest servletRequest) {
        return ApiSuccess.of(service.resend(CurrentPrincipal.require(authentication), invitationId,
                request.version(), RequestMetadata.from(servletRequest)), RequestIds.currentRequestId());
    }

    @PostMapping("/client-invitations/{invitationId}/revoke")
    ApiSuccess<ClientPortalService.InvitationView> revoke(@PathVariable UUID invitationId,
            @Valid @RequestBody VersionRequest request, Authentication authentication,
            HttpServletRequest servletRequest) {
        return ApiSuccess.of(service.revoke(CurrentPrincipal.require(authentication), invitationId,
                request.version(), RequestMetadata.from(servletRequest)), RequestIds.currentRequestId());
    }

    @PostMapping("/client-invitations/inspect")
    ApiSuccess<ClientPortalService.PublicInvitation> inspect(@Valid @RequestBody TokenRequest request) {
        return ApiSuccess.of(service.inspect(request.token()), RequestIds.currentRequestId());
    }

    @PostMapping("/client-invitations/accept")
    ApiSuccess<ClientPortalService.AcceptanceView> accept(@Valid @RequestBody AcceptRequest request,
                                                          HttpServletRequest servletRequest) {
        return ApiSuccess.of(service.accept(request.token(), request.password(),
                RequestMetadata.from(servletRequest)), RequestIds.currentRequestId());
    }

    @PostMapping("/client-auth/login")
    ResponseEntity<ApiSuccess<ClientPortalService.ClientUserView>> login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest, HttpServletResponse response) {
        var result = service.login(request.email(), request.password(), request.organizationSlug(),
                RequestMetadata.from(servletRequest));
        cookies.issue(response, result.rawSessionToken());
        return ResponseEntity.ok(ApiSuccess.of(result.user(), RequestIds.currentRequestId()));
    }

    @PostMapping("/client-auth/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    ApiSuccess<MessageView> forgot(@Valid @RequestBody AccountLookupRequest request,
                                   HttpServletRequest servletRequest) {
        service.forgotPassword(request.email(), request.organizationSlug(), RequestMetadata.from(servletRequest));
        return ApiSuccess.of(new MessageView("If the account is eligible, a reset link has been sent."),
                RequestIds.currentRequestId());
    }

    @GetMapping("/client-portal/projects")
    ApiSuccess<List<ClientPortalService.PortalProjectView>> projects(Authentication authentication,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return ApiSuccess.of(service.projectList(CurrentPrincipal.require(authentication), page, size),
                RequestIds.currentRequestId());
    }

    @GetMapping("/client-portal/projects/{projectId}")
    ApiSuccess<ClientPortalService.PortalDashboard> dashboard(@PathVariable UUID projectId,
                                                               Authentication authentication) {
        return ApiSuccess.of(service.dashboard(CurrentPrincipal.require(authentication), projectId),
                RequestIds.currentRequestId());
    }

    @PostMapping("/client-portal/projects/{projectId}/steps/{stepId}/transition")
    ApiSuccess<ClientPortalService.PortalDashboard> transition(@PathVariable UUID projectId,
            @PathVariable UUID stepId, @Valid @RequestBody StepRequest request,
            Authentication authentication, HttpServletRequest servletRequest) {
        return ApiSuccess.of(service.transition(CurrentPrincipal.require(authentication), projectId, stepId,
                new ClientPortalService.ClientStepCommand(request.targetStatus(), request.version()),
                RequestMetadata.from(servletRequest)), RequestIds.currentRequestId());
    }

    public record InviteRequest(@NotNull UUID contactId, @NotNull ClientInvitation.ClientRole role) { }
    public record VersionRequest(@PositiveOrZero long version) { }
    public record TokenRequest(@NotBlank @Size(max = 200) String token) { }
    public record AcceptRequest(@NotBlank @Size(max = 200) String token,
                                @NotBlank @Size(max = 200) String password) { }
    public record LoginRequest(@NotBlank @Email @Size(max = 254) String email,
                               @NotBlank @Size(max = 200) String password,
                               @NotBlank @Size(max = 80) String organizationSlug) { }
    public record AccountLookupRequest(@NotBlank @Email @Size(max = 254) String email,
                                       @NotBlank @Size(max = 80) String organizationSlug) { }
    public record MessageView(String message) { }
    public record StepRequest(@NotNull OnboardingStepInstance.Status targetStatus,
                              @PositiveOrZero long version) { }
}
