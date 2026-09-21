package com.brainserve.clientonboarding.auth.api;

import com.brainserve.clientonboarding.auth.application.AuthService;
import com.brainserve.clientonboarding.common.security.CurrentPrincipal;
import com.brainserve.clientonboarding.auth.application.SessionCookieService;
import com.brainserve.clientonboarding.common.security.TenantPrincipal;
import com.brainserve.clientonboarding.common.api.ApiSuccess;
import com.brainserve.clientonboarding.common.observability.RequestIds;
import com.brainserve.clientonboarding.common.observability.RequestMetadata;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final SessionCookieService cookies;

    public AuthController(AuthService authService, SessionCookieService cookies) {
        this.authService = authService;
        this.cookies = cookies;
    }

    @GetMapping("/csrf")
    ApiSuccess<CsrfView> csrf(CsrfToken csrfToken) {
        return ApiSuccess.of(new CsrfView(csrfToken.getHeaderName(), csrfToken.getParameterName(),
                csrfToken.getToken()), RequestIds.currentRequestId());
    }

    @PostMapping("/login")
    ResponseEntity<ApiSuccess<LoginView>> login(@Valid @RequestBody LoginRequest request,
                                                HttpServletRequest servletRequest,
                                                HttpServletResponse response) {
        var outcome = authService.login(new AuthService.LoginCommand(
                request.email(), request.password(), request.organizationSlug()), metadata(servletRequest));
        if (outcome.rawSessionToken() != null) cookies.issue(response, outcome.rawSessionToken());
        HttpStatus status = outcome.state() == AuthService.LoginState.AUTHENTICATED
                ? HttpStatus.OK : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(ApiSuccess.of(toView(outcome), RequestIds.currentRequestId()));
    }

    @PostMapping("/mfa/complete")
    ApiSuccess<LoginView> completeMfa(@Valid @RequestBody MfaRequest request,
                                      HttpServletRequest servletRequest,
                                      HttpServletResponse response) {
        var outcome = authService.completeMfa(request.challengeToken(), request.code(), metadata(servletRequest));
        cookies.issue(response, outcome.rawSessionToken());
        return ApiSuccess.of(toView(outcome), RequestIds.currentRequestId());
    }

    @PostMapping("/refresh")
    ApiSuccess<MessageView> refresh(Authentication authentication, HttpServletRequest request,
                                    HttpServletResponse response) {
        TenantPrincipal principal = CurrentPrincipal.require(authentication);
        var session = authService.refresh(principal, metadata(request));
        cookies.issue(response, session.rawToken());
        return ApiSuccess.of(new MessageView("Session rotated."), RequestIds.currentRequestId());
    }

    @PostMapping("/logout")
    ApiSuccess<MessageView> logout(Authentication authentication, HttpServletRequest request,
                                   HttpServletResponse response) {
        authService.logout(CurrentPrincipal.require(authentication), metadata(request));
        cookies.clear(response);
        return ApiSuccess.of(new MessageView("Signed out."), RequestIds.currentRequestId());
    }

    @GetMapping("/me")
    ApiSuccess<AuthService.UserView> me(Authentication authentication) {
        TenantPrincipal principal = CurrentPrincipal.require(authentication);
        var user = new AuthService.UserView(principal.userId(), principal.organizationId(),
                principal.organizationName(), principal.organizationSlug(),
                principal.email(), principal.displayName(), principal.roleName(), principal.permissions(),
                principal.sessionId());
        return ApiSuccess.of(user, RequestIds.currentRequestId());
    }

    @PostMapping("/forgot-password")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.ACCEPTED)
    ApiSuccess<MessageView> forgot(@Valid @RequestBody AccountLookupRequest request,
                                   HttpServletRequest servletRequest) {
        authService.forgotPassword(request.email(), request.organizationSlug(), metadata(servletRequest));
        return ApiSuccess.of(new MessageView("If the account is eligible, a reset link has been sent."),
                RequestIds.currentRequestId());
    }

    @PostMapping("/reset-password")
    ApiSuccess<MessageView> reset(@Valid @RequestBody ResetRequest request,
                                  HttpServletRequest servletRequest) {
        authService.resetPassword(request.token(), request.password(), metadata(servletRequest));
        return ApiSuccess.of(new MessageView("Password reset. Sign in again."), RequestIds.currentRequestId());
    }

    @PostMapping("/verify-email")
    ApiSuccess<MessageView> verify(@Valid @RequestBody ResetRequest request,
                                   HttpServletRequest servletRequest) {
        authService.verifyEmail(request.token(), request.password(), metadata(servletRequest));
        return ApiSuccess.of(new MessageView("Account activated. You can now sign in."),
                RequestIds.currentRequestId());
    }

    @PostMapping("/accept-invitation")
    ApiSuccess<MessageView> acceptInvitation(@Valid @RequestBody ResetRequest request,
                                              HttpServletRequest servletRequest) {
        authService.acceptInvitation(request.token(), request.password(), metadata(servletRequest));
        return ApiSuccess.of(new MessageView("Invitation accepted. You can now sign in."),
                RequestIds.currentRequestId());
    }

    @PostMapping("/email-verification/resend")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.ACCEPTED)
    ApiSuccess<MessageView> resend(@Valid @RequestBody AccountLookupRequest request,
                                   HttpServletRequest servletRequest) {
        authService.resendVerification(request.email(), request.organizationSlug(), metadata(servletRequest));
        return ApiSuccess.of(new MessageView("If the invitation is eligible, a new link has been sent."),
                RequestIds.currentRequestId());
    }

    private RequestMetadata metadata(HttpServletRequest request) {
        return RequestMetadata.from(request);
    }

    private LoginView toView(AuthService.LoginOutcome outcome) {
        return new LoginView(outcome.state(), outcome.challengeToken(), outcome.enrollmentSecret(),
                outcome.otpAuthUri(), outcome.recoveryCodes(), outcome.user());
    }

    public record LoginRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 200) String password,
            @NotBlank @Size(max = 80) String organizationSlug
    ) { }

    public record AccountLookupRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 80) String organizationSlug
    ) { }

    public record ResetRequest(
            @NotBlank @Size(max = 200) String token,
            @NotBlank @Size(max = 200) String password
    ) { }

    public record MfaRequest(
            @NotBlank @Size(max = 200) String challengeToken,
            @NotBlank @Size(max = 40) String code
    ) { }

    public record CsrfView(String headerName, String parameterName, String token) { }
    public record MessageView(String message) { }
    public record LoginView(AuthService.LoginState state, String challengeToken, String enrollmentSecret,
                            String otpAuthUri, List<String> recoveryCodes, AuthService.UserView user) { }
}
