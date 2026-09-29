package com.brainserve.clientonboarding.assets.application;

import com.brainserve.clientonboarding.assets.domain.model.AssetModels.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Explicit response allowlists: no storage keys, scan leases or provider identifiers leave the API. */
public final class AssetViews {
    private AssetViews() { }
    public record File(UUID id,int versionNumber,String filename,String mime,long byteSize,Status status,
                       ScanStatus scanStatus,String scanMessage,String reviewNote,Instant createdAt,boolean downloadable) {
        public static File of(FileVersion f) { return f==null?null:new File(f.id(),f.versionNumber(),f.filename(),f.declaredMime(),f.byteSize(),f.status(),f.scanStatus(),f.scanMessage(),f.reviewNote(),f.createdAt(),f.scanStatus()==ScanStatus.CLEAN); }
    }
    public record View(UUID projectId,UUID stepId,String stepName,String stepStatus,String projectStatus,String onboardingStatus,
                       Instant deadline,boolean allowSkip,boolean allowReopen,Requirement requirement,long version,File current) { }
    public record Version(File file,List<Review> reviews) { }
    public record Upload(View asset,AssetStorage.SignedUrl upload) { }
}
