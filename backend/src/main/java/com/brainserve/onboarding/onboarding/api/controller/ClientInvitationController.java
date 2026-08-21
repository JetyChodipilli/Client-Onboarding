package com.brainserve.onboarding.onboarding.api.controller;

import com.brainserve.onboarding.auth.api.response.AuthResponse;
import com.brainserve.onboarding.common.api.ApiResponse;
import com.brainserve.onboarding.common.observability.RequestContext;
import com.brainserve.onboarding.common.security.TenantPrincipal;
import com.brainserve.onboarding.onboarding.api.request.AcceptClientInvitationRequest;
import com.brainserve.onboarding.onboarding.api.request.ClientInvitationVersionRequest;
import com.brainserve.onboarding.onboarding.api.request.CreateClientInvitationRequest;
import com.brainserve.onboarding.onboarding.api.request.PreviewClientInvitationRequest;
import com.brainserve.onboarding.onboarding.api.response.ClientInvitationContactResponse;
import com.brainserve.onboarding.onboarding.api.response.ClientInvitationPreviewResponse;
import com.brainserve.onboarding.onboarding.api.response.ClientInvitationResponse;
import com.brainserve.onboarding.onboarding.application.service.ClientInvitationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ClientInvitationController {
    private final ClientInvitationService service;
    public ClientInvitationController(ClientInvitationService service){this.service=service;}

    @GetMapping("/api/v1/onboardings/{onboardingId}/invitations")
    @PreAuthorize("hasAuthority('ONBOARDING_READ')")
    ApiResponse<List<ClientInvitationResponse>> list(@AuthenticationPrincipal TenantPrincipal principal,@PathVariable UUID onboardingId){
        return ApiResponse.success(service.list(principal,onboardingId),RequestContext.requestId());
    }
    @GetMapping("/api/v1/onboardings/{onboardingId}/invitation-contacts")
    @PreAuthorize("hasAuthority('ONBOARDING_START')")
    ApiResponse<List<ClientInvitationContactResponse>> contacts(@AuthenticationPrincipal TenantPrincipal principal,@PathVariable UUID onboardingId){
        return ApiResponse.success(service.contacts(principal,onboardingId),RequestContext.requestId());
    }
    @PostMapping("/api/v1/onboardings/{onboardingId}/invitations")
    @PreAuthorize("hasAuthority('ONBOARDING_START')")
    ApiResponse<ClientInvitationResponse> create(@AuthenticationPrincipal TenantPrincipal principal,@PathVariable UUID onboardingId,
                                                  @Valid @RequestBody CreateClientInvitationRequest body,HttpServletRequest request){
        return ApiResponse.success(service.create(principal,onboardingId,body,request),RequestContext.requestId());
    }
    @PostMapping("/api/v1/onboardings/{onboardingId}/invitations/{invitationId}/resend")
    @PreAuthorize("hasAuthority('ONBOARDING_START')")
    ApiResponse<ClientInvitationResponse> resend(@AuthenticationPrincipal TenantPrincipal principal,@PathVariable UUID onboardingId,
                                                  @PathVariable UUID invitationId,@Valid @RequestBody ClientInvitationVersionRequest body,HttpServletRequest request){
        return ApiResponse.success(service.resend(principal,onboardingId,invitationId,body.version(),request),RequestContext.requestId());
    }
    @PostMapping("/api/v1/onboardings/{onboardingId}/invitations/{invitationId}/revoke")
    @PreAuthorize("hasAuthority('ONBOARDING_START')")
    ApiResponse<ClientInvitationResponse> revoke(@AuthenticationPrincipal TenantPrincipal principal,@PathVariable UUID onboardingId,
                                                  @PathVariable UUID invitationId,@Valid @RequestBody ClientInvitationVersionRequest body,HttpServletRequest request){
        return ApiResponse.success(service.revoke(principal,onboardingId,invitationId,body.version(),request),RequestContext.requestId());
    }

    @PostMapping("/api/v1/client-invitations/preview")
    ApiResponse<ClientInvitationPreviewResponse> preview(@Valid @RequestBody PreviewClientInvitationRequest body,HttpServletRequest request){
        return ApiResponse.success(service.preview(body.token(),request),RequestContext.requestId());
    }
    @PostMapping("/api/v1/client-invitations/accept")
    ApiResponse<AuthResponse> accept(@Valid @RequestBody AcceptClientInvitationRequest body,HttpServletRequest request,HttpServletResponse response){
        return ApiResponse.success(service.accept(body,request,response),RequestContext.requestId());
    }
}
