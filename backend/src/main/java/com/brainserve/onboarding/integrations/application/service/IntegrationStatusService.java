package com.brainserve.onboarding.integrations.application.service;

import com.brainserve.onboarding.assets.infrastructure.config.AssetScanProperties;
import com.brainserve.onboarding.assets.infrastructure.config.AssetStorageProperties;
import com.brainserve.onboarding.contracts.infrastructure.config.ContractProperties;
import com.brainserve.onboarding.contracts.infrastructure.config.ContractStorageProperties;
import com.brainserve.onboarding.contracts.infrastructure.provider.ESignatureProviderRegistry;
import com.brainserve.onboarding.integrations.api.response.IntegrationStatusResponse;
import com.brainserve.onboarding.payments.infrastructure.config.PaymentProperties;
import com.brainserve.onboarding.payments.infrastructure.provider.PaymentGatewayRegistry;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/** Safe operational projection. It exposes capability state only and never returns credentials or secret material. */
@Service
public class IntegrationStatusService {
    private final PaymentProperties payments;
    private final PaymentGatewayRegistry paymentGateways;
    private final ContractProperties contracts;
    private final ESignatureProviderRegistry eSignatures;
    private final AssetStorageProperties assetStorage;
    private final ContractStorageProperties contractStorage;
    private final AssetScanProperties assetScan;
    private final Environment environment;

    public IntegrationStatusService(PaymentProperties payments, PaymentGatewayRegistry paymentGateways,
                                    ContractProperties contracts, ESignatureProviderRegistry eSignatures,
                                    AssetStorageProperties assetStorage, ContractStorageProperties contractStorage,
                                    AssetScanProperties assetScan, Environment environment) {
        this.payments = payments;
        this.paymentGateways = paymentGateways;
        this.contracts = contracts;
        this.eSignatures = eSignatures;
        this.assetStorage = assetStorage;
        this.contractStorage = contractStorage;
        this.assetScan = assetScan;
        this.environment = environment;
    }

    public IntegrationStatusResponse status() {
        List<IntegrationStatusResponse.IntegrationStatusItem> items = new ArrayList<>();
        items.add(provider("payments", "Payments", "Financial", payments.provider(), paymentGateways.isAvailable(payments.provider())));
        items.add(provider("esignature", "E-signature", "Legal", contracts.provider(), eSignatures.isAvailable(contracts.provider())));
        items.add(toggle("asset-storage", "Asset object storage", "Storage", assetStorage.enabled(), true,
                assetStorage.enabled() ? "Private object storage is enabled for client assets." : "Asset storage is disabled."));
        items.add(toggle("contract-storage", "Signed-contract storage", "Storage", contractStorage.enabled(), true,
                contractStorage.enabled() ? "Private object storage is enabled for signed legal artifacts." : "Contract storage is disabled."));
        items.add(toggle("malware-scan", "Malware scanning", "Security", assetScan.enabled(), true,
                assetScan.enabled() ? "Uploaded assets must pass malware scanning before normal availability." : "Malware scanning is disabled."));
        String smtpHost = environment.getProperty("spring.mail.host", "");
        String from = environment.getProperty("app.mail.from", "");
        boolean mailEnabled = !smtpHost.isBlank() && !from.isBlank();
        boolean mailProduction = mailEnabled && !from.endsWith("@local.test") && !"localhost".equalsIgnoreCase(smtpHost);
        items.add(new IntegrationStatusResponse.IntegrationStatusItem("email", "Transactional email", "Communication",
                mailEnabled ? (mailProduction ? "READY" : "DEVELOPMENT") : "DISABLED", smtpHost.isBlank() ? null : "SMTP",
                mailEnabled, mailProduction, mailEnabled ? "Transactional delivery is configured; production sender verification is deployment-managed." : "Email delivery is not configured."));
        return new IntegrationStatusResponse(items);
    }

    private static IntegrationStatusResponse.IntegrationStatusItem provider(String key, String name, String category,
                                                                             String provider, boolean available) {
        boolean development = provider != null && provider.toUpperCase(java.util.Locale.ROOT).contains("SANDBOX");
        boolean enabled = provider != null && !provider.isBlank() && !"DISABLED".equalsIgnoreCase(provider);
        boolean production = enabled && available && !development;
        String status = !enabled ? "DISABLED" : !available ? "UNAVAILABLE" : development ? "DEVELOPMENT" : "READY";
        String message = !enabled ? name + " integration is disabled."
                : !available ? "The configured provider does not resolve to an available adapter in this build."
                : development ? "A signed sandbox adapter is active for local/integration testing only."
                : "A production-capable provider adapter is available.";
        return new IntegrationStatusResponse.IntegrationStatusItem(key, name, category, status, provider, enabled, production, message);
    }

    private static IntegrationStatusResponse.IntegrationStatusItem toggle(String key, String name, String category,
                                                                           boolean enabled, boolean productionCapable,
                                                                           String message) {
        return new IntegrationStatusResponse.IntegrationStatusItem(key, name, category, enabled ? "READY" : "DISABLED", null,
                enabled, enabled && productionCapable, message);
    }
}
