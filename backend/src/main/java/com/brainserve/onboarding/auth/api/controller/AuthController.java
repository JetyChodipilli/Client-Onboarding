package com.brainserve.onboarding.auth.api.controller;

import com.brainserve.onboarding.auth.api.request.AcceptInvitationRequest;
import com.brainserve.onboarding.auth.api.request.ChangePasswordRequest;
import com.brainserve.onboarding.auth.api.request.ForgotPasswordRequest;
import com.brainserve.onboarding.auth.api.request.LoginRequest;
import com.brainserve.onboarding.auth.api.request.MfaSetupConfirmRequest;
import com.brainserve.onboarding.auth.api.request.MfaSetupStartRequest;
import com.brainserve.onboarding.auth.api.request.MfaVerifyRequest;
import com.brainserve.onboarding.auth.api.request.ResendVerificationRequest;
import com.brainserve.onboarding.auth.api.request.ResetPasswordRequest;
import com.brainserve.onboarding.auth.api.request.VerifyEmailRequest;
import com.brainserve.onboarding.auth.api.response.ActionAcceptedResponse;
import com.brainserve.onboarding.auth.api.response.AuthResponse;
import com.brainserve.onboarding.auth.api.response.AuthUserResponse;
import com.brainserve.onboarding.auth.api.response.MfaSetupResponse;
import com.brainserve.onboarding.auth.application.service.AuthService;
import com.brainserve.onboarding.common.api.ApiResponse;
import com.brainserve.onboarding.common.observability.RequestContext;
import com.brainserve.onboarding.common.security.BrowserOriginPolicy;
import com.brainserve.onboarding.common.security.TenantPrincipal;
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
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final BrowserOriginPolicy browserOriginPolicy;

    public AuthController(AuthService authService, BrowserOriginPolicy browserOriginPolicy) {
        this.authService = authService;
        this.browserOriginPolicy = browserOriginPolicy;
    }

    @PostMapping("/login")
    ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        return ApiResponse.success(authService.login(body, request, response), RequestContext.requestId());
    }

    @PostMapping("/refresh")
    ApiResponse<AuthResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        browserOriginPolicy.requireTrustedOriginForCookieCommand(request);
        return ApiResponse.success(authService.refresh(request, response), RequestContext.requestId());
    }

    @PostMapping("/logout")
    ApiResponse<ActionAcceptedResponse> logout(HttpServletRequest request, HttpServletResponse response) {
        browserOriginPolicy.requireTrustedOriginForCookieCommand(request);
        authService.logout(request, response);
        return ApiResponse.success(new ActionAcceptedResponse("Signed out."), RequestContext.requestId());
    }

    @PostMapping("/forgot-password")
    ResponseEntity<ApiResponse<ActionAcceptedResponse>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest body,
                                                                        HttpServletRequest request) {
        authService.forgotPassword(body.email(), body.organizationSlug(), request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(
                new ActionAcceptedResponse("If the account exists, password-reset instructions will be sent."),
                RequestContext.requestId()));
    }

    @PostMapping("/reset-password")
    ApiResponse<ActionAcceptedResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest body, HttpServletRequest request) {
        authService.resetPassword(body.token(), body.newPassword(), request);
        return ApiResponse.success(new ActionAcceptedResponse("Password reset complete. Sign in again."), RequestContext.requestId());
    }

    @PostMapping("/verify-email")
    ApiResponse<ActionAcceptedResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequest body, HttpServletRequest request) {
        authService.verifyEmail(body.token(), request);
        return ApiResponse.success(new ActionAcceptedResponse("Email verified."), RequestContext.requestId());
    }

    @PostMapping("/resend-verification")
    ResponseEntity<ApiResponse<ActionAcceptedResponse>> resendVerification(@Valid @RequestBody ResendVerificationRequest body,
                                                                            HttpServletRequest request) {
        authService.resendVerification(body.email(), body.organizationSlug(), request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(
                new ActionAcceptedResponse("If verification is required, a new verification email will be sent."),
                RequestContext.requestId()));
    }

    @PostMapping("/mfa/verify")
    ApiResponse<AuthResponse> verifyMfa(@Valid @RequestBody MfaVerifyRequest body,
                                         HttpServletRequest request, HttpServletResponse response) {
        return ApiResponse.success(authService.verifyMfa(body.challengeToken(), body.code(), request, response),
                RequestContext.requestId());
    }

    @PostMapping("/mfa/setup/start")
    ApiResponse<MfaSetupResponse> startMfaSetup(@Valid @RequestBody MfaSetupStartRequest body,
                                                 HttpServletRequest request) {
        return ApiResponse.success(authService.beginMfaSetup(body.setupToken(), request), RequestContext.requestId());
    }

    @PostMapping("/mfa/setup/confirm")
    ApiResponse<AuthResponse> confirmMfaSetup(@Valid @RequestBody MfaSetupConfirmRequest body,
                                               HttpServletRequest request, HttpServletResponse response) {
        return ApiResponse.success(authService.confirmMfaSetup(body.setupToken(), body.code(), request, response),
                RequestContext.requestId());
    }

    @PostMapping("/invitations/accept")
    ApiResponse<AuthResponse> acceptInvitation(@Valid @RequestBody AcceptInvitationRequest body,
                                                HttpServletRequest request, HttpServletResponse response) {
        return ApiResponse.success(authService.acceptInvitation(body, request, response), RequestContext.requestId());
    }

    @GetMapping("/me")
    @PreAuthorize("hasAuthority('INTERNAL_SESSION')")
    ApiResponse<AuthUserResponse> me(@AuthenticationPrincipal TenantPrincipal principal) {
        return ApiResponse.success(authService.me(principal), RequestContext.requestId());
    }

    @PostMapping("/change-password")
    @PreAuthorize("hasAuthority('INTERNAL_SESSION')")
    ApiResponse<ActionAcceptedResponse> changePassword(@AuthenticationPrincipal TenantPrincipal principal,
                                                        @Valid @RequestBody ChangePasswordRequest body,
                                                        HttpServletRequest request, HttpServletResponse response) {
        authService.changePassword(principal, body, request, response);
        return ApiResponse.success(new ActionAcceptedResponse("Password changed. Sign in again."), RequestContext.requestId());
    }
}
