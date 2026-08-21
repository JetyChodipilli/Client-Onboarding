package com.brainserve.onboarding.contracts.infrastructure.storage;
import java.time.Instant;
public interface ContractDocumentStorage{
 boolean available(); StoredDocument storeSignedPdf(String objectKey,byte[] bytes); DownloadOperation presignDownload(String bucket,String key,String filename);
 record StoredDocument(String bucket,String key,String sha256,long sizeBytes,String contentType,Instant storedAt){}
 record DownloadOperation(String url,Instant expiresAt){}
}
