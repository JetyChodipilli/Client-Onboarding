package com.brainserve.onboarding.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class ProductionReadinessValidatorTest {
    @Test void acceptsOnlyExplicitHttpsProductionOrigins() {
        assertThat(ProductionReadinessValidator.isProductionHttpsOrigin("https://app.example.com")).isTrue();
        assertThat(ProductionReadinessValidator.isProductionHttpsOrigin("http://app.example.com")).isFalse();
        assertThat(ProductionReadinessValidator.isProductionHttpsOrigin("https://localhost:3000")).isFalse();
        assertThat(ProductionReadinessValidator.isProductionHttpsOrigin("https://*.example.com")).isFalse();
        assertThat(ProductionReadinessValidator.isProductionHttpsOrigin("https://user:pw@app.example.com")).isFalse();
    }

    @Test void rejectsDisabledAndSandboxProviders() {
        assertThat(ProductionReadinessValidator.isNonProductionProvider("DISABLED")).isTrue();
        assertThat(ProductionReadinessValidator.isNonProductionProvider("SIGNED_SANDBOX")).isTrue();
        assertThat(ProductionReadinessValidator.isNonProductionProvider("STRIPE")).isFalse();
    }
}
