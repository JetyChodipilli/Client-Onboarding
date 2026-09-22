package com.brainserve.clientonboarding.auth.infrastructure.security;

import com.brainserve.clientonboarding.auth.application.SecureTokenService;
import com.brainserve.clientonboarding.auth.application.ClientSessionAccessPort;
import com.brainserve.clientonboarding.auth.application.MfaAssurance;
import com.brainserve.clientonboarding.common.security.TenantPrincipal;
import com.brainserve.clientonboarding.auth.domain.repository.AuthRepository;
import com.brainserve.clientonboarding.auth.infrastructure.configuration.AuthProperties;
import com.brainserve.clientonboarding.organization.domain.repository.OrganizationAccessRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class SessionAuthenticationFilter extends OncePerRequestFilter {
    public static final String SESSION_COOKIE = "BOS_SESSION";
    private static final Duration TOUCH_INTERVAL = Duration.ofMinutes(5);

    private final AuthRepository authRepository;
    private final OrganizationAccessRepository accessRepository;
    private final ClientSessionAccessPort clientAccess;
    private final SecureTokenService tokens;
    private final AuthProperties properties;
    private final Clock clock;

    public SessionAuthenticationFilter(AuthRepository authRepository,
                                       OrganizationAccessRepository accessRepository,
                                       ClientSessionAccessPort clientAccess,
                                       SecureTokenService tokens,
                                       AuthProperties properties,
                                       Clock clock) {
        this.authRepository = authRepository;
        this.accessRepository = accessRepository;
        this.clientAccess = clientAccess;
        this.tokens = tokens;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String rawToken = cookie(request, SESSION_COOKIE);
        if (rawToken != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            var now = clock.instant();
            authRepository.findSessionByTokenHash(tokens.hash(rawToken)).ifPresent(session -> {
                var idleCutoff = now.minus(properties.sessionIdleTimeout());
                if (!session.isUsableAt(now, idleCutoff)) {
                    authRepository.revokeSession(session.id(), now);
                    return;
                }
                var internal = accessRepository.findByUserAndOrganization(session.userId(), session.organizationId())
                        .filter(access -> access.isUsableInternalAccess()
                                && access.user().isActiveAt(now)
                                && access.user().credentialVersion() == session.credentialVersion());
                if (internal.isPresent()) {
                    var access = internal.get();
                            if (MfaAssurance.requiredFor(access.permissions()) && !session.hasMfaAssurance()) {
                                authRepository.revokeSession(session.id(), now);
                                return;
                            }
                            var principal = new TenantPrincipal(access.user().id(), access.organization().id(),
                                    access.organization().name(), access.organization().slug(), access.membershipId(),
                                    access.user().email(), access.user().displayName(),
                                    access.roleName(), access.permissions(), session.id(), session.hasMfaAssurance());
                            var authorities = access.permissions().stream().map(SimpleGrantedAuthority::new).toList();
                            var authentication = UsernamePasswordAuthenticationToken.authenticated(
                                    principal, null, authorities);
                            SecurityContextHolder.getContext().setAuthentication(authentication);
                            if (session.lastSeenAt().isBefore(now.minus(TOUCH_INTERVAL))) {
                                authRepository.touchSession(session.id(), now);
                            }
                    return;
                }
                clientAccess.findByUserAndOrganization(session.userId(), session.organizationId())
                        .filter(access -> access.user().isActiveAt(now)
                                && access.user().credentialVersion() == session.credentialVersion())
                        .ifPresentOrElse(access -> {
                            var principal = new TenantPrincipal(access.user().id(), access.organizationId(),
                                    access.organizationName(), access.organizationSlug(), access.clientUserId(),
                                    access.user().email(), access.user().displayName(),
                                    "CLIENT_" + access.clientRole(), access.permissions(), session.id(), false);
                            var authorities = access.permissions().stream().map(SimpleGrantedAuthority::new).toList();
                            SecurityContextHolder.getContext().setAuthentication(
                                    UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities));
                            if (session.lastSeenAt().isBefore(now.minus(TOUCH_INTERVAL))) {
                                authRepository.touchSession(session.id(), now);
                            }
                        }, () -> authRepository.revokeSession(session.id(), now));
            });
        }
        chain.doFilter(request, response);
    }

    private String cookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies()).filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue).findFirst().orElse(null);
    }
}
