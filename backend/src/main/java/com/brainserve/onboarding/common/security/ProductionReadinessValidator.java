package com.brainserve.onboarding.common.security;

import com.brainserve.onboarding.assets.infrastructure.config.AssetScanProperties;
import com.brainserve.onboarding.assets.infrastructure.config.AssetStorageProperties;
import com.brainserve.onboarding.contracts.infrastructure.config.ContractProperties;
import com.brainserve.onboarding.contracts.infrastructure.config.ContractStorageProperties;
import com.brainserve.onboarding.contracts.infrastructure.provider.ESignatureProviderRegistry;
import com.brainserve.onboarding.organization.infrastructure.config.BootstrapProperties;
import com.brainserve.onboarding.payments.infrastructure.config.PaymentProperties;
import com.brainserve.onboarding.payments.infrastructure.provider.PaymentGatewayRegistry;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Fail-closed deployment gate for the production profile. It does not replace infrastructure policy,
 * but prevents common insecure local defaults from reaching a production runtime unnoticed.
 */
@Component
@Profile("prod")
public class ProductionReadinessValidator implements ApplicationRunner {
    private final SecurityProperties security;
    private final BootstrapProperties bootstrap;
    private final AssetStorageProperties assetStorage;
    private final AssetScanProperties assetScan;
    private final ContractProperties contracts;
    private final ContractStorageProperties contractStorage;
    private final PaymentProperties payments;
    private final PaymentGatewayRegistry paymentGateways;
    private final ESignatureProviderRegistry eSignatures;
    private final Environment environment;

    public ProductionReadinessValidator(SecurityProperties security, BootstrapProperties bootstrap,
                                        AssetStorageProperties assetStorage, AssetScanProperties assetScan,
                                        ContractProperties contracts, ContractStorageProperties contractStorage,
                                        PaymentProperties payments, PaymentGatewayRegistry paymentGateways,
                                        ESignatureProviderRegistry eSignatures, Environment environment) {
        this.security = security; this.bootstrap = bootstrap; this.assetStorage = assetStorage; this.assetScan = assetScan;
        this.contracts = contracts; this.contractStorage = contractStorage; this.payments = payments;
        this.paymentGateways = paymentGateways; this.eSignatures = eSignatures; this.environment = environment;
    }

    @Override public void run(ApplicationArguments args) {
        List<String> failures = new ArrayList<>();
        if (!security.requireExplicitSecrets()) failures.add("APP_REQUIRE_EXPLICIT_SECRETS must be true");
        if (!security.secureCookies()) failures.add("SECURE_COOKIES must be true");
        if (security.publicApiDocs()) failures.add("PUBLIC_API_DOCS must be false");
        if (bootstrap.enabled()) failures.add("APP_BOOTSTRAP_ENABLED must be false after initial provisioning");
        for (String origin : security.allowedOrigins()) {
            if (!isProductionHttpsOrigin(origin)) failures.add("APP_ALLOWED_ORIGINS must contain only explicit HTTPS origins: " + origin);
        }
        if (!assetStorage.enabled()) failures.add("ASSET_STORAGE_ENABLED must be true");
        if (!assetScan.enabled()) failures.add("MALWARE_SCAN_ENABLED must be true");
        if (!contractStorage.enabled()) failures.add("CONTRACT_STORAGE_ENABLED must be true");
        if (isNonProductionProvider(payments.provider())) failures.add("PAYMENT_PROVIDER must be a production adapter, not " + payments.provider());
        else if (!paymentGateways.isAvailable(payments.provider())) failures.add("PAYMENT_PROVIDER adapter is not available in this build: " + payments.provider());
        if (isNonProductionProvider(contracts.provider())) failures.add("CONTRACT_PROVIDER must be a production adapter, not " + contracts.provider());
        else if (!eSignatures.isAvailable(contracts.provider())) failures.add("CONTRACT_PROVIDER adapter is not available in this build: " + contracts.provider());
        String frontend = environment.getProperty("app.mail.frontend-base-url", "");
        if (!isProductionHttpsOrigin(frontend)) failures.add("FRONTEND_BASE_URL must be an HTTPS production origin");
        String mailFrom = environment.getProperty("app.mail.from", "");
        if (mailFrom.endsWith("@local.test") || mailFrom.isBlank()) failures.add("MAIL_FROM must be a production sender identity");
        if (!failures.isEmpty()) {
            throw new IllegalStateException("Production readiness validation failed:\n - " + String.join("\n - ", failures));
        }
    }

    static boolean isProductionHttpsOrigin(String value) {
        if (value == null || value.isBlank() || value.contains("*")) return false;
        try {
            URI uri = URI.create(value.trim());
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && !"localhost".equalsIgnoreCase(uri.getHost()) && !"127.0.0.1".equals(uri.getHost())
                    && uri.getUserInfo() == null && (uri.getPath() == null || uri.getPath().isEmpty() || "/".equals(uri.getPath()));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    static boolean isNonProductionProvider(String provider) {
        return provider == null || provider.isBlank() || "DISABLED".equalsIgnoreCase(provider)
                || provider.toUpperCase(java.util.Locale.ROOT).contains("SANDBOX");
    }
}
