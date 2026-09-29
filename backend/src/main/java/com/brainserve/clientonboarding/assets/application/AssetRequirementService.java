package com.brainserve.clientonboarding.assets.application;

import com.brainserve.clientonboarding.assets.domain.model.AssetModels.Requirement;
import com.brainserve.clientonboarding.assets.domain.model.AssetPolicy;
import com.brainserve.clientonboarding.assets.domain.repository.AssetRepository;
import com.brainserve.clientonboarding.audit.application.AuditService;
import com.brainserve.clientonboarding.common.domain.model.PageSlice;
import com.brainserve.clientonboarding.common.observability.RequestMetadata;
import com.brainserve.clientonboarding.common.security.TenantPrincipal;
import java.time.Clock;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetRequirementService {
    private final AssetRepository repository;
    private final AuditService audit;
    private final Clock clock;
    public AssetRequirementService(AssetRepository repository,AuditService audit,Clock clock) { this.repository=repository;this.audit=audit;this.clock=clock; }
    @PreAuthorize("hasAnyAuthority('ASSET_READ','ASSET_MANAGE','ASSET_REVIEW')")
    public PageSlice<Requirement> list(TenantPrincipal p,String search,int page,int size) {
        AssetErrors.page(page,size);
        if(search==null||search.length()>180)throw AssetErrors.invalid("Search must be at most 180 characters.");
        return repository.requirements(p.organizationId(),search.trim(),page,size);
    }
    @PreAuthorize("hasAuthority('ASSET_MANAGE')")
    @Transactional
    public Requirement create(TenantPrincipal p,String name,String instructions,List<String> mimes,long maxBytes,RequestMetadata metadata) {
        try { AssetPolicy.requirement(mimes,maxBytes); }catch(IllegalArgumentException e) {throw AssetErrors.invalid(e.getMessage());}
        if(name==null||name.isBlank()||name.length()>180||instructions!=null&&instructions.length()>2000)throw AssetErrors.invalid("Provide a name and instructions within the size limits.");
        var r=new Requirement(UUID.randomUUID(),p.organizationId(),name.trim(),instructions,List.copyOf(mimes),maxBytes,null,clock.instant(),0);
        repository.insertRequirement(r,p.userId());
        audit.append(p.organizationId(),p.userId(),"ASSET_REQUIREMENT_CREATED","ASSET_REQUIREMENT",r.id(),Map.of(),Map.of(),"API",metadata.ipHash());return r;
    }
    @PreAuthorize("hasAuthority('ASSET_MANAGE')")
    @Transactional
    public void archive(TenantPrincipal p,UUID id,long version,RequestMetadata metadata) {
        if(!repository.lockRequirement(p.organizationId(),id))throw AssetErrors.missing();
        if(!repository.archive(p.organizationId(),id,version,p.userId(),clock.instant()))throw AssetErrors.conflict();
        audit.append(p.organizationId(),p.userId(),"ASSET_REQUIREMENT_ARCHIVED","ASSET_REQUIREMENT",id,Map.of(),Map.of(),"API",metadata.ipHash());
    }
}
