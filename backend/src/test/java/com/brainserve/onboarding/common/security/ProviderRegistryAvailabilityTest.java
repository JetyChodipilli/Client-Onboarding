package com.brainserve.onboarding.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.brainserve.onboarding.contracts.infrastructure.config.ContractProperties;
import com.brainserve.onboarding.contracts.infrastructure.provider.ESignatureProvider;
import com.brainserve.onboarding.contracts.infrastructure.provider.ESignatureProviderRegistry;
import com.brainserve.onboarding.payments.infrastructure.config.PaymentProperties;
import com.brainserve.onboarding.payments.infrastructure.provider.PaymentGateway;
import com.brainserve.onboarding.payments.infrastructure.provider.PaymentGatewayRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProviderRegistryAvailabilityTest {
    @Test void paymentRegistryDoesNotTreatAnUnknownProductionLookingCodeAsAvailable() {
        PaymentGatewayRegistry registry = new PaymentGatewayRegistry(
                List.of(new PaymentStub("SIGNED_SANDBOX", true)),
                new PaymentProperties("STRIPE", null, null, Duration.ofMinutes(5), Duration.ofMinutes(30), 262_144));
        assertThat(registry.isAvailable("STRIPE")).isFalse();
        assertThat(registry.isAvailable("SIGNED_SANDBOX")).isTrue();
    }

    @Test void contractRegistryRequiresAnActuallyAvailableAdapter() {
        ESignatureProviderRegistry registry = new ESignatureProviderRegistry(
                List.of(new ContractStub("SIGNED_SANDBOX", true)),
                new ContractProperties("DOCUSIGN", null, null, Duration.ofMinutes(5), 262_144, 10_000_000));
        assertThat(registry.isAvailable("DOCUSIGN")).isFalse();
        assertThat(registry.isAvailable("SIGNED_SANDBOX")).isTrue();
    }

    private record PaymentStub(String providerCode, boolean available) implements PaymentGateway {
        @Override public CheckoutSession createCheckout(CheckoutCommand command) { throw new UnsupportedOperationException(); }
        @Override public RefundRequest requestRefund(RefundCommand command) { throw new UnsupportedOperationException(); }
        @Override public VerifiedWebhook verifyWebhook(byte[] body, Map<String, String> headers, Instant now) { throw new UnsupportedOperationException(); }
    }

    private record ContractStub(String providerCode, boolean available) implements ESignatureProvider {
        @Override public SendResult send(SendCommand command) { throw new UnsupportedOperationException(); }
        @Override public void voidDocument(String providerDocumentId, String reason) { throw new UnsupportedOperationException(); }
        @Override public VerifiedCallback verifyWebhook(byte[] body, Map<String, String> headers, Instant now) { throw new UnsupportedOperationException(); }
        @Override public SignedDocument downloadSignedDocument(String providerDocumentId) { throw new UnsupportedOperationException(); }
    }
}
