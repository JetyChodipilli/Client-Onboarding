package com.brainserve.onboarding.contracts.infrastructure.storage;
public class DisabledContractDocumentStorage implements ContractDocumentStorage{
 public boolean available(){return false;} public StoredDocument storeSignedPdf(String key,byte[] bytes){throw new IllegalStateException("Contract document storage is disabled");} public DownloadOperation presignDownload(String bucket,String key,String filename){throw new IllegalStateException("Contract document storage is disabled");}
}
