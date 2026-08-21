package com.brainserve.onboarding.common.security;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
public class JwtSupportConfig {

    @Bean
    Clock applicationClock() { return Clock.systemUTC(); }

    @Bean
    JwtEncoder jwtEncoder(SecurityKeyMaterial keyMaterial) {
        SecretKey key = keyMaterial.jwtKey();
        return NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS512).build();
    }

    @Bean
    JwtDecoder jwtDecoder(SecurityKeyMaterial keyMaterial, SecurityProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(keyMaterial.jwtKey()).macAlgorithm(MacAlgorithm.HS512).build();
        OAuth2TokenValidator<Jwt> defaults = JwtValidators.createDefaultWithIssuer(properties.tokenIssuer());
        OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<List<String>>("aud",
                values -> values != null && values.contains(properties.tokenAudience()));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(defaults, audience));
        return decoder;
    }

    @Bean
    Converter<Jwt, AbstractAuthenticationToken> tenantJwtAuthenticationConverter(
            TenantAccessService internalAccess, ClientTenantAccessService clientAccess) {
        return jwt -> {
            boolean clientToken = "CLIENT".equals(jwt.getClaimAsString("pt"));
            AuthenticatedPrincipal principal = clientToken ? clientAccess.loadPrincipal(jwt) : internalAccess.loadPrincipal(jwt);
            List<SimpleGrantedAuthority> authorities = new ArrayList<>(
                    principal.permissions().stream().map(SimpleGrantedAuthority::new).toList());
            // Synthetic scope markers ensure an external client JWT can never be reused on internal account APIs.
            authorities.add(new SimpleGrantedAuthority(clientToken ? "CLIENT_SESSION" : "INTERNAL_SESSION"));
            return UsernamePasswordAuthenticationToken.authenticated(principal, jwt, authorities);
        };
    }
}
