package com.brainserve.clientonboarding.assets.infrastructure.persistence;

import static com.brainserve.clientonboarding.common.infrastructure.persistence.JdbcValues.timestamp;
import com.brainserve.clientonboarding.assets.domain.model.AssetModels.*;
import com.brainserve.clientonboarding.assets.domain.repository.AssetRepository;
import com.brainserve.clientonboarding.common.domain.model.PageSlice;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAssetRepository implements AssetRepository {
    private final JdbcClient jdbc;
    public JdbcAssetRepository(JdbcClient jdbc) { this.jdbc=jdbc; }
    public PageSlice<Requirement> requirements(UUID org,String search,int page,int size) {
        String filter=" FROM asset_requirements WHERE organization_id=:org AND archived_at IS NULL AND lower(name) LIKE :search";
        var args=Map.of("org",org,"search","%"+search.toLowerCase(Locale.ROOT)+"%","limit",size,"offset",(long)page*size);
        return new PageSlice<>(jdbc.sql("SELECT *"+filter+" ORDER BY created_at DESC,id LIMIT :limit OFFSET :offset").params(args).query(this::requirement).list(),page,size,
                jdbc.sql("SELECT count(*)"+filter).params(args).query(Long.class).single());
    }
    public Optional<Requirement> requirement(UUID org,UUID id) { return jdbc.sql("SELECT * FROM asset_requirements WHERE organization_id=? AND id=?").params(org,id).query(this::requirement).optional(); }
    public void insertRequirement(Requirement r,UUID actor) {
        jdbc.sql("INSERT INTO asset_requirements(id,organization_id,name,instructions,allowed_mimes,max_bytes,created_at,created_by,updated_at,updated_by) VALUES(?,?,?,?,?,?,?,?,?,?)")
                .params(r.id(),r.organizationId(),r.name(),r.instructions(),String.join(",",r.allowedMimes()),r.maxBytes(),timestamp(r.createdAt()),actor,timestamp(r.createdAt()),actor).update();
    }
    public boolean lockRequirement(UUID org,UUID id) { return jdbc.sql("SELECT id FROM asset_requirements WHERE organization_id=? AND id=? AND archived_at IS NULL FOR NO KEY UPDATE").params(org,id).query(UUID.class).optional().isPresent(); }
    public boolean archive(UUID org,UUID id,long version,UUID actor,Instant now) { return jdbc.sql("UPDATE asset_requirements SET archived_at=?,updated_at=?,updated_by=?,version=version+1 WHERE organization_id=? AND id=? AND version=? AND archived_at IS NULL").params(timestamp(now),timestamp(now),actor,org,id,version).update()==1; }
    public Optional<Asset> asset(UUID org,UUID stepId) { return jdbc.sql("SELECT * FROM assets WHERE organization_id=? AND step_id=?").params(org,stepId).query((r,n)->new Asset(r.getObject("id",UUID.class),org,stepId,r.getObject("requirement_id",UUID.class),r.getObject("current_version_id",UUID.class),r.getLong("version"))).optional(); }
    public void insertAsset(Asset a,UUID actor,Instant now) { jdbc.sql("INSERT INTO assets(id,organization_id,step_id,requirement_id,created_at,created_by,updated_at,updated_by) VALUES(?,?,?,?,?,?,?,?)").params(a.id(),a.organizationId(),a.stepId(),a.requirementId(),timestamp(now),actor,timestamp(now),actor).update(); }
    public boolean updateAsset(Asset a,UUID fileId,UUID actor,Instant now) { return jdbc.sql("UPDATE assets SET current_version_id=?,updated_at=?,updated_by=?,version=version+1 WHERE organization_id=? AND id=? AND version=?").params(fileId,timestamp(now),actor,a.organizationId(),a.id(),a.version()).update()==1; }
    public Optional<FileVersion> file(UUID org,UUID assetId,UUID id) { return jdbc.sql("SELECT * FROM asset_versions WHERE organization_id=? AND asset_id=? AND id=?").params(org,assetId,id).query(this::file).optional(); }
    public int nextVersion(UUID org,UUID id) { return jdbc.sql("SELECT coalesce(max(version_number),0)+1 FROM asset_versions WHERE organization_id=? AND asset_id=?").params(org,id).query(Integer.class).single(); }
    public void insertFile(FileVersion f,UUID actor) {
        jdbc.sql("INSERT INTO asset_versions(id,organization_id,asset_id,version_number,filename,declared_mime,byte_size,sha256,object_key,status,scan_status,upload_expires_at,created_at,created_by,updated_at,updated_by) VALUES(?,?,?,?,?,?,?,?,?,'REQUESTED','PENDING',?,?,?,?,?)")
                .params(f.id(),f.organizationId(),f.assetId(),f.versionNumber(),f.filename(),f.declaredMime(),f.byteSize(),f.sha256(),f.objectKey(),timestamp(f.uploadExpiresAt()),timestamp(f.createdAt()),actor,timestamp(f.createdAt()),actor).update();
    }
    public boolean updateFile(FileVersion f,UUID actor,Instant now) {
        return jdbc.sql("UPDATE asset_versions SET object_version_id=?,status=?,scan_status=?,detected_mime=?,scan_message=?,review_note=?,scan_lease_id=?,scan_lease_until=?,scanned_at=?,updated_at=?,updated_by=?,version=version+1 WHERE organization_id=? AND asset_id=? AND id=? AND version=?")
                .params(f.objectVersionId(),f.status().name(),f.scanStatus().name(),f.detectedMime(),f.scanMessage(),f.reviewNote(),f.scanLeaseId(),timestamp(f.scanLeaseUntil()),timestamp(f.scannedAt()),timestamp(now),actor,f.organizationId(),f.assetId(),f.id(),f.version()-1).update()==1;
    }
    public PageSlice<History> history(UUID org,UUID asset,int page,int size) {
        var files=jdbc.sql("SELECT * FROM asset_versions WHERE organization_id=? AND asset_id=? ORDER BY version_number DESC LIMIT ? OFFSET ?").params(org,asset,size,(long)page*size).query(this::file).list();
        Map<UUID,List<Review>> reviews=new HashMap<>();
        if(!files.isEmpty()) jdbc.sql("SELECT * FROM asset_reviews WHERE organization_id=:org AND asset_version_id IN (:ids) ORDER BY created_at,id").param("org",org).param("ids",files.stream().map(FileVersion::id).toList()).query((r,n)-> {
            reviews.computeIfAbsent(r.getObject("asset_version_id",UUID.class),id->new ArrayList<>()).add(new Review(Decision.valueOf(r.getString("decision")),r.getString("note"),instant(r,"created_at"),r.getObject("created_by",UUID.class)));return 1;
        }).list();
        return new PageSlice<>(files.stream().map(f->new History(f,reviews.getOrDefault(f.id(),List.of()))).toList(),page,size,jdbc.sql("SELECT count(*) FROM asset_versions WHERE organization_id=? AND asset_id=?").params(org,asset).query(Long.class).single());
    }
    public void review(FileVersion f,Decision decision,String note,UUID actor,Instant now) { jdbc.sql("INSERT INTO asset_reviews(id,organization_id,asset_version_id,decision,note,created_at,created_by) VALUES(?,?,?,?,?,?,?)").params(UUID.randomUUID(),f.organizationId(),f.id(),decision.name(),note,timestamp(now),actor).update(); }
    public void event(Asset a,UUID fileId,String type,String correlationId,Instant now) { jdbc.sql("INSERT INTO asset_outbox_events(id,organization_id,asset_id,asset_version_id,event_type,occurred_at,correlation_id) VALUES(?,?,?,?,?,?,?)").params(UUID.randomUUID(),a.organizationId(),a.id(),fileId,type,timestamp(now),correlationId).update(); }
    private Requirement requirement(ResultSet r,int n)throws SQLException { return new Requirement(r.getObject("id",UUID.class),r.getObject("organization_id",UUID.class),r.getString("name"),r.getString("instructions"),List.of(r.getString("allowed_mimes").split(",")),r.getLong("max_bytes"),instant(r,"archived_at"),instant(r,"created_at"),r.getLong("version")); }
    private FileVersion file(ResultSet r,int n)throws SQLException { return new FileVersion(r.getObject("id",UUID.class),r.getObject("organization_id",UUID.class),r.getObject("asset_id",UUID.class),r.getInt("version_number"),r.getString("filename"),r.getString("declared_mime"),r.getString("detected_mime"),r.getLong("byte_size"),r.getString("sha256"),r.getString("object_key"),r.getString("object_version_id"),Status.valueOf(r.getString("status")),ScanStatus.valueOf(r.getString("scan_status")),r.getString("scan_message"),r.getString("review_note"),instant(r,"upload_expires_at"),r.getObject("scan_lease_id",UUID.class),instant(r,"scan_lease_until"),instant(r,"scanned_at"),instant(r,"created_at"),r.getLong("version")); }
    private Instant instant(ResultSet r,String col)throws SQLException { var t=r.getTimestamp(col);return t==null?null:t.toInstant(); }
}
