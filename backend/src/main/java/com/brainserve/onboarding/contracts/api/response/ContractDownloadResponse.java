package com.brainserve.onboarding.contracts.api.response;
import java.time.Instant;
public record ContractDownloadResponse(String url,Instant expiresAt,String filename){}
