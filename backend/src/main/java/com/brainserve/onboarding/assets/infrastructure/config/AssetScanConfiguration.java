package com.brainserve.onboarding.assets.infrastructure.config;

import com.brainserve.onboarding.assets.infrastructure.scanning.ClamAvMalwareScanner;
import com.brainserve.onboarding.assets.infrastructure.scanning.DisabledMalwareScanner;
import com.brainserve.onboarding.assets.infrastructure.scanning.MalwareScanner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AssetScanConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "app.assets.scan", name = "enabled", havingValue = "true")
    MalwareScanner clamAvMalwareScanner(AssetScanProperties properties) { return new ClamAvMalwareScanner(properties); }

    @Bean
    @ConditionalOnMissingBean(MalwareScanner.class)
    MalwareScanner disabledMalwareScanner() { return new DisabledMalwareScanner(); }
}
