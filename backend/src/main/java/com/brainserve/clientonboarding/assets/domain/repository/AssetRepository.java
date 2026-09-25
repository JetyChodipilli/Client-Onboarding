package com.brainserve.clientonboarding.assets.domain.repository;

import com.brainserve.clientonboarding.assets.domain.model.AssetModels.*;
import com.brainserve.clientonboarding.common.domain.model.PageSlice;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AssetRepository {
    PageSlice<Requirement> requirements(UUID organizationId, String search, int page, int size);
    Optional<Requirement> requirement(UUID organizationId, UUID id);
    void insertRequirement(Requirement requirement, UUID actor);
    boolean lockRequirement(UUID organizationId, UUID id);
    boolean archive(UUID organizationId, UUID id, long version, UUID actor, Instant now);
    Optional<Asset> asset(UUID organizationId, UUID stepId);
    void insertAsset(Asset asset, UUID actor, Instant now);
    boolean updateAsset(Asset asset, UUID currentVersionId, UUID actor, Instant now);
    Optional<FileVersion> file(UUID organizationId, UUID assetId, UUID id);
    int nextVersion(UUID organizationId, UUID assetId);
    void insertFile(FileVersion file, UUID actor);
    boolean updateFile(FileVersion file, UUID actor, Instant now);
    PageSlice<History> history(UUID organizationId, UUID assetId, int page, int size);
    void review(FileVersion file, Decision decision, String note, UUID actor, Instant now);
    void event(Asset asset, UUID fileId, String type, String correlationId, Instant now);
}
