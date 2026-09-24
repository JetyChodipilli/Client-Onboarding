package com.brainserve.clientonboarding.assets.infrastructure;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("app.assets")
public class AssetProperties {
    private boolean enabled;
    private String endpoint="http://localhost:9000", publicEndpoint="http://localhost:9000", region="us-east-1", bucket="onboarding-assets";
    private String accessKey="", secretKey="", scannerHost="localhost";
    private int scannerPort=3310;
    public void validate() {
        if(!enabled) return;
        if(accessKey.isBlank() || secretKey.isBlank() || !bucket.matches("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]")) throw new IllegalStateException("Configure private asset storage credentials and bucket.");
        for(String value:new String[]{endpoint,publicEndpoint}) {
            URI uri=URI.create(value);
            if(!java.util.Set.of("http","https").contains(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null || !(uri.getPath().isEmpty() || uri.getPath().equals("/")))
                throw new IllegalStateException("Asset storage endpoints must be absolute HTTP(S) origins.");
        }
        if(scannerHost.isBlank() || scannerPort<1 || scannerPort>65535) throw new IllegalStateException("Configure the trusted malware scanner endpoint.");
    }
    public boolean isEnabled(){return enabled;} public void setEnabled(boolean v){enabled=v;}
    public String getEndpoint(){return endpoint;} public void setEndpoint(String v){endpoint=v;}
    public String getPublicEndpoint(){return publicEndpoint;} public void setPublicEndpoint(String v){publicEndpoint=v;}
    public String getRegion(){return region;} public void setRegion(String v){region=v;}
    public String getBucket(){return bucket;} public void setBucket(String v){bucket=v;}
    public String getAccessKey(){return accessKey;} public void setAccessKey(String v){accessKey=v;}
    public String getSecretKey(){return secretKey;} public void setSecretKey(String v){secretKey=v;}
    public String getScannerHost(){return scannerHost;} public void setScannerHost(String v){scannerHost=v;}
    public int getScannerPort(){return scannerPort;} public void setScannerPort(int v){scannerPort=v;}
}
