package com.brainserve.clientonboarding.assets.application;

import java.io.InputStream;
import java.time.Instant;
import java.util.Map;

public interface AssetStorage {
    SignedUrl upload(String key, String mime, long size, String sha256);
    StoredObject inspect(String key, String versionId);
    InputStream read(String key, String versionId);
    SignedUrl download(String key, String versionId, String filename);
    record SignedUrl(String url, String method, Map<String,String> headers, Instant expiresAt) { }
    record StoredObject(String versionId, long size, String mime, String sha256, Instant modifiedAt) { }
}
