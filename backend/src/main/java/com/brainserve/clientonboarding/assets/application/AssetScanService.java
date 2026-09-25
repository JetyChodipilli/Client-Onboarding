package com.brainserve.clientonboarding.assets.application;

import com.brainserve.clientonboarding.assets.domain.model.AssetModels.*;
import com.brainserve.clientonboarding.assets.domain.repository.AssetRepository;
import com.brainserve.clientonboarding.common.error.DomainException;
import com.brainserve.clientonboarding.common.observability.RequestMetadata;
import com.brainserve.clientonboarding.common.security.TenantPrincipal;
import com.brainserve.clientonboarding.onboarding.application.StepExecutionService;
import com.brainserve.clientonboarding.onboarding.domain.model.OnboardingStepInstance;
import com.brainserve.clientonboarding.portal.application.ClientPortalService;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.Semaphore;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AssetScanService {
    private static final Logger LOG=LoggerFactory.getLogger(AssetScanService.class);
    private final AssetService assets;
    private final AssetRepository repository;
    private final StepExecutionService steps;
    private final ClientPortalService portal;
    private final AssetStorage storage;
    private final MalwareScanner scanner;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final Semaphore capacity=new Semaphore(2);
    public AssetScanService(AssetService assets,AssetRepository repository,StepExecutionService steps,ClientPortalService portal,
                            AssetStorage storage,MalwareScanner scanner,Clock clock,PlatformTransactionManager transactions) {
        this.assets=assets;this.repository=repository;this.steps=steps;this.portal=portal;this.storage=storage;this.scanner=scanner;this.clock=clock;this.tx=new TransactionTemplate(transactions);
    }
    @PreAuthorize("hasAuthority('CLIENT_PORTAL_STEP_UPDATE')")
    public AssetViews.View submit(TenantPrincipal p,UUID projectId,UUID stepId,long version,RequestMetadata metadata) {
        portal.requireStepAccess(p,projectId,stepId);
        if(!capacity.tryAcquire())throw AssetErrors.state("File scanning is busy. Please retry shortly.");
        try {
            var claimed=tx.execute(status->{
                var context=steps.lockActive(p.organizationId(),stepId);portal.requireStepAccess(p,projectId,stepId);
                var view=assets.view(context);assets.editable(context);if(view.version()!=version)throw AssetErrors.conflict();
                var a=repository.asset(p.organizationId(),stepId).orElseThrow(AssetErrors::missing);var f=assets.currentFile(a);
                if(f==null||!Set.of(Status.REQUESTED,Status.UPLOADED,Status.SCANNING).contains(f.status())
                        ||f.scanLeaseUntil()!=null&&f.scanLeaseUntil().isAfter(clock.instant()))throw AssetErrors.state("This file cannot be submitted or is already being scanned.");
                var next=f.change(f.objectVersionId(),Status.SCANNING,ScanStatus.SCANNING,null,null,f.reviewNote(),UUID.randomUUID(),clock.instant().plusSeconds(300),null);
                assets.save(next,p);assets.bump(a,f.id(),p);return next;
            });
            Result result;
            try {result=inspect(p,projectId,stepId,claimed);}
            catch(IOException e){LOG.warn("Asset scan unavailable assetId={} cause={}",claimed.assetId(),e.getClass().getSimpleName());result=new Result(ScanStatus.ERROR,null,"Scanning is unavailable. Retry submission or contact your project team.");}
            catch(DomainException e){if(e.status()!=org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE)throw e;LOG.warn("Asset storage unavailable assetId={}",claimed.assetId());result=new Result(ScanStatus.ERROR,null,"Storage is unavailable. Retry submission or request a new upload.");}
            final Result outcome=result;
            return tx.execute(status->{
                var context=steps.lockActive(p.organizationId(),stepId);portal.requireStepAccess(p,projectId,stepId);
                var a=repository.asset(p.organizationId(),stepId).orElseThrow(AssetErrors::missing);var f=leased(a,claimed);
                Status target=outcome.scan()==ScanStatus.CLEAN?(context.step().requiresReview()?Status.SUBMITTED:Status.APPROVED)
                        :outcome.scan()==ScanStatus.INFECTED?Status.QUARANTINED:outcome.scan()==ScanStatus.ERROR?Status.UPLOADED:Status.REJECTED;
                assets.save(f.change(f.objectVersionId(),target,outcome.scan(),outcome.mime(),outcome.message(),f.reviewNote(),null,null,clock.instant()),p);
                assets.bump(a,f.id(),p);
                if(outcome.scan()==ScanStatus.CLEAN) {
                    steps.transition(context,context.step().requiresReview()?OnboardingStepInstance.Status.SUBMITTED:OnboardingStepInstance.Status.COMPLETED,p.userId(),clock.instant());
                    assets.log(p,a,f.id(),"ASSET_UPLOADED",metadata);
                    if(target==Status.APPROVED)assets.log(p,a,f.id(),"ASSET_APPROVED",metadata);
                }else assets.log(p,a,f.id(),"ASSET_"+target.name(),metadata);
                return assets.view(steps.read(p.organizationId(),stepId));
            });
        }finally{capacity.release();}
    }
    private Result inspect(TenantPrincipal p,UUID projectId,UUID stepId,FileVersion claimed)throws IOException {
        var object=storage.inspect(claimed.objectKey(),claimed.objectVersionId());
        if(object.size()!=claimed.byteSize()||!claimed.declaredMime().equals(object.mime())||!claimed.sha256().equals(object.sha256())
                ||object.modifiedAt()==null||object.modifiedAt().isAfter(claimed.uploadExpiresAt()))return invalid("The uploaded file does not match its authorized size, type, checksum or upload deadline. Upload a new version.");
        tx.executeWithoutResult(status->{
            steps.lockActive(p.organizationId(),stepId);portal.requireStepAccess(p,projectId,stepId);
            var a=repository.asset(p.organizationId(),stepId).orElseThrow(AssetErrors::missing);var f=leased(a,claimed);
            assets.save(f.change(object.versionId(),f.status(),f.scanStatus(),null,null,f.reviewNote(),f.scanLeaseId(),f.scanLeaseUntil(),null),p);
        });
        Path temp=Files.createTempFile("onboarding-asset-",".scan");
        try {
            MessageDigest digest;
            try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
            long count=0;long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(60);
            try(var input=storage.read(claimed.objectKey(),object.versionId());var output=Files.newOutputStream(temp)) {
                byte[] buffer=new byte[8192];int read;
                while((read=input.read(buffer))!=-1){count+=read;if(count>claimed.byteSize())return invalid("The uploaded size does not match. Upload a new version.");if(System.nanoTime()>deadline)throw new IOException("File transfer deadline exceeded");digest.update(buffer,0,read);output.write(buffer,0,read);}
            }
            if(count!=claimed.byteSize()||!HexFormat.of().formatHex(digest.digest()).equals(claimed.sha256()))return invalid("The uploaded checksum does not match. Upload a new version.");
            // Detection only: no parsing, archive extraction, active rendering or untrusted filename hints.
            String detected;
            try(var input=new BufferedInputStream(Files.newInputStream(temp))){detected=new Tika().detect(input);}
            if(scanner.scan(temp)==MalwareScanner.Verdict.INFECTED)return new Result(ScanStatus.INFECTED,detected,"This file was quarantined by malware scanning. Upload a different, safe file.");
            if(!claimed.declaredMime().equals(detected))return invalid("The file content does not match the selected type. Upload a correctly typed file.");
            return new Result(ScanStatus.CLEAN,detected,"File validation and malware scanning passed.");
        }finally{Files.deleteIfExists(temp);}
    }
    private FileVersion leased(Asset a,FileVersion claimed) {
        var f=assets.currentFile(a);
        if(f==null||!f.id().equals(claimed.id())||f.status()!=Status.SCANNING||!Objects.equals(f.scanLeaseId(),claimed.scanLeaseId()))throw AssetErrors.conflict();return f;
    }
    private Result invalid(String message){return new Result(ScanStatus.PENDING,null,message);}
    private record Result(ScanStatus scan,String mime,String message) { }
}
