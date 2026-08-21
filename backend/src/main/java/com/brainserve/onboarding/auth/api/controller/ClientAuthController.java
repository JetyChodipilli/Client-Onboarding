package com.brainserve.onboarding.auth.api.controller;

import com.brainserve.onboarding.auth.api.request.ClientForgotPasswordRequest;
import com.brainserve.onboarding.auth.api.request.ClientLoginRequest;
import com.brainserve.onboarding.auth.api.request.ClientMfaVerifyRequest;
import com.brainserve.onboarding.auth.api.request.ClientResetPasswordRequest;
import com.brainserve.onboarding.auth.api.response.ActionAcceptedResponse;
import com.brainserve.onboarding.auth.api.response.AuthResponse;
import com.brainserve.onboarding.auth.api.response.AuthUserResponse;
import com.brainserve.onboarding.auth.application.service.ClientAuthService;
import com.brainserve.onboarding.common.api.ApiResponse;
import com.brainserve.onboarding.common.observability.RequestContext;
import com.brainserve.onboarding.common.security.BrowserOriginPolicy;
import com.brainserve.onboarding.common.security.ClientPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/client-auth")
public class ClientAuthController {
    private final ClientAuthService auth;
    private final BrowserOriginPolicy originPolicy;

    public ClientAuthController(ClientAuthService auth, BrowserOriginPolicy originPolicy) {
        this.auth = auth;
        this.originPolicy = originPolicy;
    }

    @PostMapping("/login")
    ApiResponse<AuthResponse> login(@Valid @RequestBody ClientLoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        return ApiResponse.success(auth.login(body, request, response), RequestContext.requestId());
    }

    @PostMapping("/refresh")
    ApiResponse<AuthResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        originPolicy.requireTrustedOriginForCookieCommand(request);
        return ApiResponse.success(auth.refresh(request, response), RequestContext.requestId());
    }

    @PostMapping("/logout")
    ApiResponse<ActionAcceptedResponse> logout(HttpServletRequest request, HttpServletResponse response) {
        originPolicy.requireTrustedOriginForCookieCommand(request);
        auth.logout(request, response);
        return ApiResponse.success(new ActionAcceptedResponse("Signed out."), RequestContext.requestId());
    }

    @PostMapping("/forgot-password")
    ResponseEntity<ApiResponse<ActionAcceptedResponse>> forgot(@Valid @RequestBody ClientForgotPasswordRequest body,
                                                                 HttpServletRequest request) {
        auth.forgotPassword(body.email(), body.organizationSlug(), request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(
                new ActionAcceptedResponse("If the account exists, password-reset instructions will be sent."), RequestContext.requestId()));
    }

    @PostMapping("/reset-password")
    ApiResponse<ActionAcceptedResponse> reset(@Valid @RequestBody ClientResetPasswordRequest body, HttpServletRequest request) {
        auth.resetPassword(body.token(), body.newPassword(), request);
        return ApiResponse.success(new ActionAcceptedResponse("Password reset complete. Sign in again."), RequestContext.requestId());
    }

    @PostMapping("/mfa/verify")
    ApiResponse<AuthResponse> mfa(@Valid @RequestBody ClientMfaVerifyRequest body,
                                  HttpServletRequest request, HttpServletResponse response) {
        return ApiResponse.success(auth.verifyMfa(body.challengeToken(), body.code(), request, response), RequestContext.requestId());
    }

    @GetMapping("/me")
    @PreAuthorize("hasAuthority('CLIENT_SESSION')")
    ApiResponse<AuthUserResponse> me(@AuthenticationPrincipal ClientPrincipal principal) {
        return ApiResponse.success(auth.me(principal), RequestContext.requestId());
    }
}
