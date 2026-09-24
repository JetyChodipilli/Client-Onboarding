package com.brainserve.clientonboarding.assets.application;

import com.brainserve.clientonboarding.assets.domain.model.AssetModels.*;
import com.brainserve.clientonboarding.assets.domain.model.AssetPolicy;
import com.brainserve.clientonboarding.assets.domain.repository.AssetRepository;
import com.brainserve.clientonboarding.audit.application.AuditService;
import com.brainserve.clientonboarding.common.domain.model.PageSlice;
import com.brainserve.clientonboarding.common.observability.RequestMetadata;
import com.brainserve.clientonboarding.common.observability.RequestIds;
import com.brainserve.clientonboarding.common.security.TenantPrincipal;
import com.brainserve.clientonboarding.onboarding.application.StepExecutionService;
import com.brainserve.clientonboarding.onboarding.domain.model.OnboardingStepInstance;
import com.brainserve.clientonboarding.portal.application.ClientPortalService;
import com.brainserve.clientonboarding.workflow.domain.model.TemplateStep;
import java.time.Clock;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AssetService {
    private final AssetRepository repository;
    private final AssetStepConfiguration configurations;
    private final StepExecutionService steps;
    private final ClientPortalService portal;
    private final AssetStorage storage;
    private final AuditService audit;
    private final Clock clock;
    private final TransactionTemplate tx;
    public AssetService(AssetRepository repository,AssetStepConfiguration configurations,StepExecutionService steps,
                        ClientPortalService portal,AssetStorage storage,AuditService audit,Clock clock,PlatformTransactionManager transactions) {
        this.repository=repository;this.configurations=configurations;this.steps=steps;this.portal=portal;this.storage=storage;this.audit=audit;this.clock=clock;this.tx=new TransactionTemplate(transactions);
    }
    @PreAuthorize("hasAnyAuthority('ASSET_READ','ASSET_REVIEW','CLIENT_PORTAL_READ')")
    public AssetViews.View get(TenantPrincipal p,UUID projectId,UUID stepId) { authorize(p,projectId,stepId);return view(steps.read(p.organizationId(),stepId)); }
    @PreAuthorize("hasAnyAuthority('ASSET_READ','ASSET_REVIEW','CLIENT_PORTAL_READ')")
    public PageSlice<AssetViews.Version> history(TenantPrincipal p,UUID projectId,UUID stepId,int page,int size) {
        AssetErrors.page(page,size);authorize(p,projectId,stepId);view(steps.read(p.organizationId(),stepId));
        var asset=repository.asset(p.organizationId(),stepId);
        if(asset.isEmpty())return new PageSlice<>(List.of(),page,size,0);
        var rows=repository.history(p.organizationId(),asset.get().id(),page,size);
        return new PageSlice<>(rows.items().stream().map(h->new AssetViews.Version(AssetViews.File.of(h.file()),h.reviews())).toList(),page,size,rows.totalElements());
    }
    @PreAuthorize("hasAuthority('CLIENT_PORTAL_STEP_UPDATE')")
    public AssetViews.Upload upload(TenantPrincipal p,UUID projectId,UUID stepId,long version,String filename,String mime,long size,String sha256,RequestMetadata metadata) {
        portal.requireStepAccess(p,projectId,stepId);
        var file=tx.execute(status->{
            var context=steps.lockActive(p.organizationId(),stepId);portal.requireStepAccess(p,projectId,stepId);
            var current=view(context);editable(context);if(current.version()!=version)throw AssetErrors.conflict();
            String safe;
            try{AssetPolicy.upload(current.requirement(),mime,size,sha256);safe=AssetPolicy.filename(filename);}catch(IllegalArgumentException e){throw AssetErrors.invalid(e.getMessage());}
            var asset=ensureAsset(p,stepId,current.requirement().id());var old=currentFile(asset);
            if(old!=null && (!Set.of(Status.REQUESTED,Status.UPLOADED,Status.NEEDS_REVISION,Status.QUARANTINED,Status.REJECTED).contains(old.status())
                    || old.scanLeaseUntil()!=null&&old.scanLeaseUntil().isAfter(clock.instant())))throw AssetErrors.state("Wait for scanning or request a revision before replacing this file.");
            int number=repository.nextVersion(p.organizationId(),asset.id());if(number>1000)throw AssetErrors.state("This asset has reached its version limit. Contact your project team.");
            var id=UUID.randomUUID();var now=clock.instant();
            var next=new FileVersion(id,p.organizationId(),asset.id(),number,safe,mime,null,size,sha256,
                    p.organizationId()+"/"+projectId+"/"+asset.id()+"/"+id,null,Status.REQUESTED,ScanStatus.PENDING,null,null,now.plusSeconds(600),null,null,null,now,0);
            if(old!=null)save(old.status(Status.REPLACED,old.reviewNote()),p);
            repository.insertFile(next,p.userId());bump(asset,id,p);
            steps.transition(context,OnboardingStepInstance.Status.IN_PROGRESS,p.userId(),now);log(p,asset,id,"ASSET_UPLOAD_REQUESTED",metadata);return next;
        });
        // Provider calls never hold the project/database lock. A failed URL request can be safely replaced.
        var signed=storage.upload(file.objectKey(),mime,size,sha256);
        return new AssetViews.Upload(view(steps.read(p.organizationId(),stepId)),signed);
    }
    @PreAuthorize("hasAuthority('ASSET_REVIEW')")
    public AssetViews.View review(TenantPrincipal p,UUID stepId,long version,Decision decision,String note,RequestMetadata metadata) {
        return tx.execute(status->{
            var context=steps.lockActive(p.organizationId(),stepId);var current=view(context);if(current.version()!=version)throw AssetErrors.conflict();
            var asset=repository.asset(p.organizationId(),stepId).orElseThrow(AssetErrors::missing);var file=currentFile(asset);
            if(file==null||file.scanStatus()!=ScanStatus.CLEAN||!Set.of(Status.SUBMITTED,Status.UNDER_REVIEW).contains(file.status())
                    || !Set.of(OnboardingStepInstance.Status.SUBMITTED,OnboardingStepInstance.Status.UNDER_REVIEW).contains(context.step().status())
                    || !Set.of(Decision.UNDER_REVIEW,Decision.APPROVED,Decision.NEEDS_REVISION).contains(decision)
                    || decision==Decision.UNDER_REVIEW&&file.status()!=Status.SUBMITTED)throw AssetErrors.state("Only the current, scanned submission can be reviewed.");
            String clean=note(note,decision==Decision.NEEDS_REVISION);save(file.status(Status.valueOf(decision.name()),clean),p);bump(asset,file.id(),p);
            repository.review(file,decision,clean,p.userId(),clock.instant());
            steps.transition(context,decision==Decision.APPROVED?OnboardingStepInstance.Status.COMPLETED:OnboardingStepInstance.Status.valueOf(decision.name()),p.userId(),clock.instant());
            log(p,asset,file.id(),"ASSET_"+decision.name(),metadata);return view(steps.read(p.organizationId(),stepId));
        });
    }
    @PreAuthorize("hasAuthority('ASSET_REVIEW')")
    public AssetViews.View exception(TenantPrincipal p,UUID stepId,long version,boolean reopen,String note,RequestMetadata metadata) {
        return tx.execute(status->{
            var context=steps.lockActive(p.organizationId(),stepId);var current=view(context);if(current.version()!=version)throw AssetErrors.conflict();
            String clean=note(note,true);var asset=ensureAsset(p,stepId,current.requirement().id());var file=currentFile(asset);
            if(reopen ? !current.allowReopen()||context.step().status()!=OnboardingStepInstance.Status.COMPLETED||file==null||file.status()!=Status.APPROVED
                    : !current.allowSkip()||!Set.of(OnboardingStepInstance.Status.AVAILABLE,OnboardingStepInstance.Status.IN_PROGRESS).contains(context.step().status())
                        ||file!=null&&file.status()==Status.SCANNING)throw AssetErrors.state("The workflow rules do not allow this action.");
            if(file!=null){save(file.status(reopen?Status.NEEDS_REVISION:Status.REPLACED,clean),p);repository.review(file,reopen?Decision.REOPENED:Decision.SKIPPED,clean,p.userId(),clock.instant());}
            bump(asset,asset.currentVersionId(),p);
            steps.transition(context,reopen?OnboardingStepInstance.Status.NEEDS_REVISION:OnboardingStepInstance.Status.SKIPPED,p.userId(),clock.instant());
            log(p,asset,asset.currentVersionId(),reopen?"ASSET_REOPENED":"ASSET_SKIPPED",metadata);return view(steps.read(p.organizationId(),stepId));
        });
    }
    @PreAuthorize("hasAnyAuthority('ASSET_READ','ASSET_REVIEW','CLIENT_PORTAL_READ')")
    public AssetStorage.SignedUrl download(TenantPrincipal p,UUID projectId,UUID stepId,UUID fileId) {
        authorize(p,projectId,stepId);view(steps.read(p.organizationId(),stepId));
        var asset=repository.asset(p.organizationId(),stepId).orElseThrow(AssetErrors::missing);
        var file=repository.file(p.organizationId(),asset.id(),fileId).orElseThrow(AssetErrors::missing);
        if(file.scanStatus()!=ScanStatus.CLEAN||file.objectVersionId()==null)throw AssetErrors.state("This file is not available until validation and malware scanning succeed.");
        return storage.download(file.objectKey(),file.objectVersionId(),file.filename());
    }
    void authorize(TenantPrincipal p,UUID projectId,UUID stepId) {if(projectId!=null||p.hasPermission("CLIENT_PORTAL_READ"))portal.requireStepAccess(p,projectId,stepId);}
    AssetViews.View view(StepExecutionService.Context context) {
        var step=context.step();if(step.stepType()!=TemplateStep.StepType.FILE_UPLOAD||!step.applicable())throw AssetErrors.missing();
        var requirement=configurations.require(step.organizationId(),step.configuration());var asset=repository.asset(step.organizationId(),step.id()).orElse(null);
        if(asset!=null&&!asset.requirementId().equals(requirement.id()))throw new IllegalStateException("Asset binding changed after snapshot");
        return new AssetViews.View(context.onboarding().projectId(),step.id(),step.name(),step.status().name(),context.projectStatus(),context.onboarding().status().name(),step.dueAt(),step.allowSkip()&&!step.blocking(),step.allowReopen(),requirement,asset==null?0:asset.version(),AssetViews.File.of(asset==null?null:currentFile(asset)));
    }
    void editable(StepExecutionService.Context context) {if(!Set.of(OnboardingStepInstance.Status.AVAILABLE,OnboardingStepInstance.Status.IN_PROGRESS,OnboardingStepInstance.Status.NEEDS_REVISION).contains(context.step().status()))throw AssetErrors.state("This step is locked or already submitted.");}
    FileVersion currentFile(Asset a) {return a.currentVersionId()==null?null:repository.file(a.organizationId(),a.id(),a.currentVersionId()).orElseThrow(AssetErrors::missing);}
    void save(FileVersion file,TenantPrincipal p) {if(!repository.updateFile(file,p.userId(),clock.instant()))throw AssetErrors.conflict();}
    void bump(Asset a,UUID id,TenantPrincipal p) {if(!repository.updateAsset(a,id,p.userId(),clock.instant()))throw AssetErrors.conflict();}
    void log(TenantPrincipal p,Asset a,UUID id,String action,RequestMetadata metadata) {
        audit.append(p.organizationId(),p.userId(),action,"ASSET",a.id(),Map.of(),Map.of("stepId",a.stepId()),"API",metadata.ipHash());
        repository.event(a,id,action,RequestIds.currentCorrelationId(),clock.instant());
    }
    private Asset ensureAsset(TenantPrincipal p,UUID step,UUID requirement) {
        return repository.asset(p.organizationId(),step).orElseGet(()->{var a=new Asset(UUID.randomUUID(),p.organizationId(),step,requirement,null,0);repository.insertAsset(a,p.userId(),clock.instant());return a;});
    }
    private String note(String note,boolean required) {if(required&&(note==null||note.isBlank())||note!=null&&note.length()>2000)throw AssetErrors.invalid("Provide an explanation of at most 2000 characters.");return note==null?null:note.trim();}
}
