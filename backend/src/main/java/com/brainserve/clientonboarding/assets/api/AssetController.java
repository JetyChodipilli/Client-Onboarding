package com.brainserve.clientonboarding.assets.api;

import com.brainserve.clientonboarding.assets.application.*;
import com.brainserve.clientonboarding.assets.domain.model.AssetModels.*;
import com.brainserve.clientonboarding.common.api.ApiSuccess;
import com.brainserve.clientonboarding.common.domain.model.PageSlice;
import com.brainserve.clientonboarding.common.observability.RequestIds;
import com.brainserve.clientonboarding.common.observability.RequestMetadata;
import com.brainserve.clientonboarding.common.security.CurrentPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AssetController {
    private final AssetRequirementService requirements;
    private final AssetService assets;
    private final AssetScanService scans;
    public AssetController(AssetRequirementService requirements,AssetService assets,AssetScanService scans){this.requirements=requirements;this.assets=assets;this.scans=scans;}
    @GetMapping("/asset-requirements")
    ApiSuccess<List<Requirement>> list(@RequestParam(defaultValue="")String search,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="50")int size,Authentication auth){return page(requirements.list(CurrentPrincipal.require(auth),search,page,size));}
    @PostMapping("/asset-requirements") @ResponseStatus(HttpStatus.CREATED)
    ApiSuccess<Requirement> create(@Valid @RequestBody RequirementRequest r,Authentication auth,HttpServletRequest servlet){return ok(requirements.create(CurrentPrincipal.require(auth),r.name(),r.instructions(),r.allowedMimes(),r.maxBytes(),RequestMetadata.from(servlet)));}
    @PostMapping("/asset-requirements/{id}/archive")
    ApiSuccess<Map<String,String>> archive(@PathVariable UUID id,@RequestParam @PositiveOrZero long version,Authentication auth,HttpServletRequest servlet){requirements.archive(CurrentPrincipal.require(auth),id,version,RequestMetadata.from(servlet));return ok(Map.of("message","Requirement archived. Existing project files are retained."));}
    @GetMapping("/asset-responses/{stepId}")
    ApiSuccess<AssetViews.View> internal(@PathVariable UUID stepId,Authentication auth){return ok(assets.get(CurrentPrincipal.require(auth),null,stepId));}
    @GetMapping("/asset-responses/{stepId}/versions")
    ApiSuccess<List<AssetViews.Version>> history(@PathVariable UUID stepId,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="10")int size,Authentication auth){return page(assets.history(CurrentPrincipal.require(auth),null,stepId,page,size));}
    @PostMapping("/asset-responses/{stepId}/review")
    ApiSuccess<AssetViews.View> review(@PathVariable UUID stepId,@Valid @RequestBody ReviewRequest r,Authentication auth,HttpServletRequest servlet){return ok(assets.review(CurrentPrincipal.require(auth),stepId,r.version(),r.decision(),r.note(),RequestMetadata.from(servlet)));}
    @PostMapping("/asset-responses/{stepId}/reopen")
    ApiSuccess<AssetViews.View> reopen(@PathVariable UUID stepId,@Valid @RequestBody ExceptionRequest r,Authentication auth,HttpServletRequest servlet){return ok(assets.exception(CurrentPrincipal.require(auth),stepId,r.version(),true,r.note(),RequestMetadata.from(servlet)));}
    @PostMapping("/asset-responses/{stepId}/skip")
    ApiSuccess<AssetViews.View> skip(@PathVariable UUID stepId,@Valid @RequestBody ExceptionRequest r,Authentication auth,HttpServletRequest servlet){return ok(assets.exception(CurrentPrincipal.require(auth),stepId,r.version(),false,r.note(),RequestMetadata.from(servlet)));}
    @PostMapping("/asset-responses/{stepId}/versions/{fileId}/download")
    ApiSuccess<AssetStorage.SignedUrl> download(@PathVariable UUID stepId,@PathVariable UUID fileId,Authentication auth){return ok(assets.download(CurrentPrincipal.require(auth),null,stepId,fileId));}
    @GetMapping("/client-portal/projects/{projectId}/assets/{stepId}")
    ApiSuccess<AssetViews.View> client(@PathVariable UUID projectId,@PathVariable UUID stepId,Authentication auth){return ok(assets.get(CurrentPrincipal.require(auth),projectId,stepId));}
    @GetMapping("/client-portal/projects/{projectId}/assets/{stepId}/versions")
    ApiSuccess<List<AssetViews.Version>> clientHistory(@PathVariable UUID projectId,@PathVariable UUID stepId,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="10")int size,Authentication auth){return page(assets.history(CurrentPrincipal.require(auth),projectId,stepId,page,size));}
    @PostMapping("/client-portal/projects/{projectId}/assets/{stepId}/upload-url")
    ApiSuccess<AssetViews.Upload> upload(@PathVariable UUID projectId,@PathVariable UUID stepId,@Valid @RequestBody UploadRequest r,Authentication auth,HttpServletRequest servlet){return ok(assets.upload(CurrentPrincipal.require(auth),projectId,stepId,r.version(),r.filename(),r.mime(),r.byteSize(),r.sha256(),RequestMetadata.from(servlet)));}
    @PostMapping("/client-portal/projects/{projectId}/assets/{stepId}/submit")
    ApiSuccess<AssetViews.View> submit(@PathVariable UUID projectId,@PathVariable UUID stepId,@Valid @RequestBody VersionRequest r,Authentication auth,HttpServletRequest servlet){return ok(scans.submit(CurrentPrincipal.require(auth),projectId,stepId,r.version(),RequestMetadata.from(servlet)));}
    @PostMapping("/client-portal/projects/{projectId}/assets/{stepId}/versions/{fileId}/download")
    ApiSuccess<AssetStorage.SignedUrl> clientDownload(@PathVariable UUID projectId,@PathVariable UUID stepId,@PathVariable UUID fileId,Authentication auth){return ok(assets.download(CurrentPrincipal.require(auth),projectId,stepId,fileId));}
    private <T> ApiSuccess<T> ok(T value){return ApiSuccess.of(value,RequestIds.currentRequestId());}
    private <T> ApiSuccess<List<T>> page(PageSlice<T> value){return new ApiSuccess<>(true,value.items(),value.meta(),RequestIds.currentRequestId());}
    public record RequirementRequest(@NotBlank @Size(max=180)String name,@Size(max=2000)String instructions,@NotNull @Size(min=1,max=7)List<@NotBlank String> allowedMimes,@Min(1) @Max(52428800)long maxBytes){}
    public record UploadRequest(@PositiveOrZero long version,@NotBlank @Size(max=500)String filename,@NotBlank @Size(max=100)String mime,@Min(1) @Max(52428800)long byteSize,@NotBlank @Pattern(regexp="[0-9a-f]{64}")String sha256){}
    public record VersionRequest(@PositiveOrZero long version){}
    public record ReviewRequest(@PositiveOrZero long version,@NotNull Decision decision,@Size(max=2000)String note){}
    public record ExceptionRequest(@PositiveOrZero long version,@NotBlank @Size(max=2000)String note){}
}
