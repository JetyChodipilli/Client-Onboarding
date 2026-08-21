package com.brainserve.onboarding.common.config;

import com.brainserve.onboarding.common.security.SecurityProperties;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityBaselineConfig {

    private static final String[] PUBLIC_AUTH_POSTS = {
        "/api/v1/auth/login",
        "/api/v1/auth/refresh",
        "/api/v1/auth/logout",
        "/api/v1/auth/forgot-password",
        "/api/v1/auth/reset-password",
        "/api/v1/auth/verify-email",
        "/api/v1/auth/resend-verification",
        "/api/v1/auth/invitations/accept",
        "/api/v1/auth/mfa/verify",
        "/api/v1/auth/mfa/setup/start",
        "/api/v1/auth/mfa/setup/confirm",
        "/api/v1/client-auth/login",
        "/api/v1/client-auth/refresh",
        "/api/v1/client-auth/logout",
        "/api/v1/client-auth/forgot-password",
        "/api/v1/client-auth/reset-password",
        "/api/v1/client-auth/mfa/verify",
        "/api/v1/client-invitations/preview",
        "/api/v1/client-invitations/accept"
    };

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityProblemHandler problemHandler,
            SecurityProperties properties,
            Converter<Jwt, AbstractAuthenticationToken> tenantJwtAuthenticationConverter) throws Exception {
        http
                .cors(Customizer.withDefaults())
                // Access tokens are Bearer tokens. The only browser credential cookie is a SameSite=Strict,
                // HttpOnly refresh cookies scoped separately to internal and client-auth paths. Exact-origin CORS is also enforced, and
                // BrowserOriginPolicy separately guards the cookie-only refresh/logout commands.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(tenantJwtAuthenticationConverter))
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler))
                .authorizeHttpRequests(authorize -> {
                    authorize.requestMatchers(HttpMethod.GET, "/health/live", "/health/ready").permitAll();
                    authorize.requestMatchers(HttpMethod.POST, PUBLIC_AUTH_POSTS).permitAll();
                    authorize.requestMatchers(HttpMethod.POST, "/api/v1/webhooks/payments/**", "/api/v1/webhooks/contracts/**").permitAll();
                    if (properties.publicApiDocs()) {
                        authorize.requestMatchers(
                                        HttpMethod.GET,
                                        "/v3/api-docs/**",
                                        "/swagger-ui/**",
                                        "/swagger-ui.html")
                                .permitAll();
                    }
                    authorize.requestMatchers("/api/v1/**").authenticated();
                    authorize.anyRequest().denyAll();
                })
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .contentTypeOptions(Customizer.withDefaults()));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(SecurityProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "Idempotency-Key", "X-Request-Id", "X-Correlation-Id"));
        configuration.setExposedHeaders(List.of("X-Request-Id", "X-Correlation-Id"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
