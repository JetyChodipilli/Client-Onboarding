package com.brainserve.onboarding.contracts.infrastructure.config;
import java.time.Duration;import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties("app.contracts.storage")
public record ContractStorageProperties(boolean enabled,String endpoint,String region,String bucket,String accessKey,String secretKey,boolean pathStyleAccess,Duration downloadUrlTtl){
 public ContractStorageProperties{region=blank(region)?"us-east-1":region.trim();bucket=blank(bucket)?"client-onboarding-contracts":bucket.trim();downloadUrlTtl=downloadUrlTtl==null?Duration.ofMinutes(10):downloadUrlTtl;if(enabled&&(blank(endpoint)||blank(accessKey)||blank(secretKey)))throw new IllegalStateException("Enabled contract storage requires endpoint, access key, and secret key");if(enabled&&(downloadUrlTtl.isZero()||downloadUrlTtl.isNegative()||downloadUrlTtl.compareTo(Duration.ofHours(1))>0))throw new IllegalStateException("Contract download URL TTL must be between 1 second and 1 hour");}
 private static boolean blank(String v){return v==null||v.isBlank();}
}
