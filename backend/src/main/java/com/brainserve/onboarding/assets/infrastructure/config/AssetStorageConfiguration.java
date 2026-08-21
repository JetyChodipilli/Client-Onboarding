package com.brainserve.onboarding.assets.infrastructure.config;

import com.brainserve.onboarding.assets.infrastructure.storage.AssetObjectStorage;
import com.brainserve.onboarding.assets.infrastructure.storage.DisabledAssetObjectStorage;
import com.brainserve.onboarding.assets.infrastructure.storage.S3AssetObjectStorage;
import java.net.URI;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class AssetStorageConfiguration {
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "app.assets.storage", name = "enabled", havingValue = "true")
    S3Client assetS3Client(AssetStorageProperties properties) {
        var credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
        var service = S3Configuration.builder().pathStyleAccessEnabled(properties.pathStyleAccess()).build();
        return S3Client.builder()
                .endpointOverride(URI.create(properties.endpoint()))
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials)
                .serviceConfiguration(service)
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "app.assets.storage", name = "enabled", havingValue = "true")
    S3Presigner assetS3Presigner(AssetStorageProperties properties) {
        var credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
        var service = S3Configuration.builder().pathStyleAccessEnabled(properties.pathStyleAccess()).build();
        return S3Presigner.builder()
                .endpointOverride(URI.create(properties.endpoint()))
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials)
                .serviceConfiguration(service)
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.assets.storage", name = "enabled", havingValue = "true")
    AssetObjectStorage s3AssetObjectStorage(AssetStorageProperties properties, @Qualifier("assetS3Client") S3Client assetS3Client,
                                            @Qualifier("assetS3Presigner") S3Presigner assetS3Presigner, Clock clock) {
        return new S3AssetObjectStorage(properties, assetS3Client, assetS3Presigner, clock);
    }

    @Bean
    @ConditionalOnMissingBean(AssetObjectStorage.class)
    AssetObjectStorage disabledAssetObjectStorage() { return new DisabledAssetObjectStorage(); }
}
