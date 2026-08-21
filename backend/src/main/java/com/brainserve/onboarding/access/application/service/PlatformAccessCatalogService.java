package com.brainserve.onboarding.access.application.service;

import com.brainserve.onboarding.access.api.request.*;
import com.brainserve.onboarding.access.api.response.*;
import com.brainserve.onboarding.access.domain.model.*;
import com.brainserve.onboarding.access.infrastructure.persistence.*;
import com.brainserve.onboarding.audit.application.service.AuditService;
import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.common.security.TenantPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformAccessCatalogService {
    private static final int MAX_PAGE_SIZE = 100;
    private final PlatformAccessTypeRepository types;
    private final PlatformAccessGuideVersionRepository versions;
    private final PlatformAccessSafetyPolicy safety;
    private final AuditService audit;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PlatformAccessCatalogService(PlatformAccessTypeRepository types, PlatformAccessGuideVersionRepository versions,
                                        PlatformAccessSafetyPolicy safety, AuditService audit, ObjectMapper objectMapper, Clock clock) {
        this.types=types; this.versions=versions; this.safety=safety; this.audit=audit; this.objectMapper=objectMapper; this.clock=clock;
    }

    @Transactional
    public PlatformAccessTypeDetailResponse create(TenantPrincipal principal, CreatePlatformAccessTypeRequest request, HttpServletRequest servletRequest) {
        String code;
        try { code=PlatformAccessType.normalizeCode(request.code()); } catch (IllegalArgumentException ex) { throw invalid(ex.getMessage()); }
        if (types.existsByOrganizationIdAndCode(principal.organizationId(), code)) throw new ApiException(HttpStatus.CONFLICT,"ACCESS_TYPE_CODE_EXISTS","An access type with this code already exists.");
        safety.validateGuide(request.instructionsMarkdown(), request.helpUrl(), objectMapper.createArrayNode());
        Instant now=clock.instant();
        PlatformAccessType type;
        try { type=types.saveAndFlush(new PlatformAccessType(UUID.randomUUID(),principal.organizationId(),code,request.name(),request.description(),principal.userId(),now)); }
        catch (IllegalArgumentException ex) { throw invalid(ex.getMessage()); }
        PlatformAccessGuideVersion guide=versions.saveAndFlush(new PlatformAccessGuideVersion(UUID.randomUUID(),principal.organizationId(),type.getId(),1,request.changeNote(),request.instructionsMarkdown(),request.helpUrl(),objectMapper.createArrayNode(),principal.userId(),now));
        audit.record(principal.organizationId(),principal.userId(),"PLATFORM_ACCESS_TYPE_CREATED","PLATFORM_ACCESS_TYPE",type.getId(),null,Map.of("code",type.getCode(),"name",type.getName(),"draftVersion",guide.getVersionNumber()),servletRequest);
        return detail(type);
    }

    @Transactional(readOnly=true)
    public PageResult<PlatformAccessTypeSummaryResponse> list(TenantPrincipal principal,int page,int size,boolean includeArchived) {
        Pageable pageable=PageRequest.of(Math.max(0,page),safeSize(size),Sort.by(Sort.Direction.ASC,"name").and(Sort.by("id")));
        Page<PlatformAccessType> result=includeArchived?types.findAllByOrganizationId(principal.organizationId(),pageable):types.findAllByOrganizationIdAndStatus(principal.organizationId(),PlatformAccessTypeStatus.ACTIVE,pageable);
        Set<UUID> ids=result.getContent().stream().map(PlatformAccessType::getId).collect(Collectors.toSet());
        Map<UUID,Integer> published=ids.isEmpty()?Map.of():versions.findAllByOrganizationIdAndAccessTypeIdIn(principal.organizationId(),ids).stream().filter(v->v.getStatus()==PlatformAccessGuideVersionStatus.PUBLISHED).collect(Collectors.toMap(PlatformAccessGuideVersion::getAccessTypeId,PlatformAccessGuideVersion::getVersionNumber,Integer::max));
        List<PlatformAccessTypeSummaryResponse> items=result.getContent().stream().map(t->summary(t,published.get(t.getId()))).toList();
        return new PageResult<>(items,result.getNumber(),result.getSize(),result.getTotalElements(),result.getTotalPages());
    }

    @Transactional(readOnly=true)
    public PlatformAccessTypeDetailResponse get(TenantPrincipal principal,UUID id){ return detail(requireType(principal.organizationId(),id)); }

    @Transactional
    public PlatformAccessTypeDetailResponse update(TenantPrincipal principal,UUID id,UpdatePlatformAccessTypeRequest request,HttpServletRequest servletRequest){
        PlatformAccessType type=requireTypeForUpdate(principal.organizationId(),id); requireVersion(type.getVersion(),request.version());
        Map<String,Object> before=Map.of("name",type.getName(),"description",safe(type.getDescription()),"version",type.getVersion());
        try { type.update(request.name(),request.description(),principal.userId(),clock.instant()); } catch(IllegalArgumentException|IllegalStateException ex){ throw invalid(ex.getMessage()); }
        types.saveAndFlush(type);
        audit.record(principal.organizationId(),principal.userId(),"PLATFORM_ACCESS_TYPE_UPDATED","PLATFORM_ACCESS_TYPE",id,before,Map.of("name",type.getName(),"description",safe(type.getDescription()),"version",type.getVersion()),servletRequest);
        return detail(type);
    }

    @Transactional
    public PlatformAccessTypeDetailResponse archive(TenantPrincipal principal,UUID id,long expectedVersion,HttpServletRequest servletRequest){
        PlatformAccessType type=requireTypeForUpdate(principal.organizationId(),id); requireVersion(type.getVersion(),expectedVersion);
        type.archive(principal.userId(),clock.instant()); types.saveAndFlush(type);
        audit.record(principal.organizationId(),principal.userId(),"PLATFORM_ACCESS_TYPE_ARCHIVED","PLATFORM_ACCESS_TYPE",id,null,Map.of("status",type.getStatus().name()),servletRequest);
        return detail(type);
    }

    @Transactional
    public PlatformAccessGuideResponse createVersion(TenantPrincipal principal,UUID typeId,CreatePlatformAccessGuideVersionRequest request,HttpServletRequest servletRequest){
        PlatformAccessType type=requireTypeForUpdate(principal.organizationId(),typeId);
        if(type.getStatus()!=PlatformAccessTypeStatus.ACTIVE) throw new ApiException(HttpStatus.CONFLICT,"ACCESS_TYPE_ARCHIVED","Archived access types cannot be versioned.");
        if(versions.existsByOrganizationIdAndAccessTypeIdAndStatus(principal.organizationId(),typeId,PlatformAccessGuideVersionStatus.DRAFT)) throw new ApiException(HttpStatus.CONFLICT,"ACCESS_GUIDE_DRAFT_EXISTS","Publish or edit the existing draft before creating another guide version.");
        PlatformAccessGuideVersion source=versions.findFirstByOrganizationIdAndAccessTypeIdAndStatusOrderByVersionNumberDesc(principal.organizationId(),typeId,PlatformAccessGuideVersionStatus.PUBLISHED).orElse(null);
        int number=source==null?1:source.getVersionNumber()+1; Instant now=clock.instant();
        String instructions=source==null?"Grant access using the platform's own user/role invitation controls. Never share passwords or authentication secrets.":source.getInstructionsMarkdown();
        String help=source==null?null:source.getHelpUrl();
        var resources=source==null?objectMapper.createArrayNode():source.getResourcesJson();
        PlatformAccessGuideVersion target=versions.saveAndFlush(new PlatformAccessGuideVersion(UUID.randomUUID(),principal.organizationId(),typeId,number,request.changeNote(),instructions,help,resources,principal.userId(),now));
        audit.record(principal.organizationId(),principal.userId(),"PLATFORM_ACCESS_GUIDE_VERSION_CREATED","PLATFORM_ACCESS_GUIDE_VERSION",target.getId(),null,Map.of("accessTypeId",typeId,"versionNumber",number),servletRequest);
        return guide(target);
    }

    @Transactional(readOnly=true)
    public PlatformAccessGuideResponse getVersion(TenantPrincipal principal,UUID versionId){ return guide(requireGuide(principal.organizationId(),versionId)); }

    @Transactional
    public PlatformAccessGuideResponse saveDraft(TenantPrincipal principal,UUID versionId,SavePlatformAccessGuideRequest request,HttpServletRequest servletRequest){
        PlatformAccessGuideVersion guide=requireGuideForUpdate(principal.organizationId(),versionId); requireVersion(guide.getVersion(),request.version());
        requireActiveType(principal.organizationId(),guide.getAccessTypeId()); safety.validateGuide(request.instructionsMarkdown(),request.helpUrl(),request.resources());
        try { guide.revise(request.changeNote(),request.instructionsMarkdown(),request.helpUrl(),request.resources(),principal.userId(),clock.instant()); } catch(IllegalArgumentException|IllegalStateException ex){ throw invalid(ex.getMessage()); }
        versions.saveAndFlush(guide); audit.record(principal.organizationId(),principal.userId(),"PLATFORM_ACCESS_GUIDE_DRAFT_SAVED","PLATFORM_ACCESS_GUIDE_VERSION",guide.getId(),null,Map.of("versionNumber",guide.getVersionNumber()),servletRequest); return guide(guide);
    }

    @Transactional
    public PlatformAccessGuideResponse publish(TenantPrincipal principal,UUID versionId,long expectedVersion,HttpServletRequest servletRequest){
        PlatformAccessGuideVersion guide=requireGuideForUpdate(principal.organizationId(),versionId); requireVersion(guide.getVersion(),expectedVersion); requireActiveType(principal.organizationId(),guide.getAccessTypeId());
        safety.validateGuide(guide.getInstructionsMarkdown(),guide.getHelpUrl(),guide.getResourcesJson());
        try { guide.publish(principal.userId(),clock.instant()); } catch(IllegalStateException ex){ throw new ApiException(HttpStatus.CONFLICT,"ACCESS_GUIDE_IMMUTABLE",ex.getMessage()); }
        versions.saveAndFlush(guide); audit.record(principal.organizationId(),principal.userId(),"PLATFORM_ACCESS_GUIDE_PUBLISHED","PLATFORM_ACCESS_GUIDE_VERSION",guide.getId(),null,Map.of("accessTypeId",guide.getAccessTypeId(),"versionNumber",guide.getVersionNumber()),servletRequest); return guide(guide);
    }

    @Transactional(readOnly=true)
    public List<PublishedPlatformAccessGuideResponse> publishedVersions(TenantPrincipal principal){
        Map<UUID,PlatformAccessType> typeMap=types.findAllByOrganizationIdAndStatus(principal.organizationId(),PlatformAccessTypeStatus.ACTIVE,PageRequest.of(0,100,Sort.by("name"))).getContent().stream().collect(Collectors.toMap(PlatformAccessType::getId,Function.identity()));
        return versions.findAllByOrganizationIdAndStatusOrderByUpdatedAtDescIdDesc(principal.organizationId(),PlatformAccessGuideVersionStatus.PUBLISHED).stream().filter(v->typeMap.containsKey(v.getAccessTypeId())).map(v->{var t=typeMap.get(v.getAccessTypeId());return new PublishedPlatformAccessGuideResponse(t.getId(),t.getCode(),t.getName(),v.getId(),v.getVersionNumber());}).sorted(Comparator.comparing(PublishedPlatformAccessGuideResponse::name,String.CASE_INSENSITIVE_ORDER).thenComparing(PublishedPlatformAccessGuideResponse::versionNumber).reversed()).toList();
    }

    @Transactional(readOnly=true)
    public PublishedGuide requirePublished(UUID organizationId,UUID versionId){
        PlatformAccessGuideVersion version=requireGuide(organizationId,versionId); if(version.getStatus()!=PlatformAccessGuideVersionStatus.PUBLISHED) throw new ApiException(HttpStatus.BAD_REQUEST,"ACCESS_GUIDE_NOT_PUBLISHED","Platform access workflow steps must reference a published guide version.");
        PlatformAccessType type=requireType(organizationId,version.getAccessTypeId()); if(type.getStatus()!=PlatformAccessTypeStatus.ACTIVE) throw new ApiException(HttpStatus.BAD_REQUEST,"ACCESS_TYPE_ARCHIVED","Platform access workflow steps cannot reference an archived access type.");
        return new PublishedGuide(type,version);
    }

    private PlatformAccessTypeDetailResponse detail(PlatformAccessType type){ List<PlatformAccessGuideResponse> list=versions.findAllByOrganizationIdAndAccessTypeIdOrderByVersionNumberDesc(type.getOrganizationId(),type.getId()).stream().map(PlatformAccessCatalogService::guide).toList(); return new PlatformAccessTypeDetailResponse(type.getId(),type.getCode(),type.getName(),type.getDescription(),type.getStatus(),type.getUpdatedAt(),type.getVersion(),list); }
    private static PlatformAccessTypeSummaryResponse summary(PlatformAccessType t,Integer published){return new PlatformAccessTypeSummaryResponse(t.getId(),t.getCode(),t.getName(),t.getDescription(),t.getStatus(),published,t.getUpdatedAt(),t.getVersion());}
    private static PlatformAccessGuideResponse guide(PlatformAccessGuideVersion v){return new PlatformAccessGuideResponse(v.getId(),v.getAccessTypeId(),v.getVersionNumber(),v.getStatus(),v.getChangeNote(),v.getInstructionsMarkdown(),v.getHelpUrl(),v.getResourcesJson(),v.getPublishedAt(),v.getUpdatedAt(),v.getVersion());}
    private PlatformAccessType requireType(UUID org,UUID id){return types.findByOrganizationIdAndId(org,id).orElseThrow(PlatformAccessCatalogService::notFound);}
    private PlatformAccessType requireTypeForUpdate(UUID org,UUID id){return types.findForUpdate(org,id).orElseThrow(PlatformAccessCatalogService::notFound);}
    private PlatformAccessGuideVersion requireGuide(UUID org,UUID id){return versions.findByOrganizationIdAndId(org,id).orElseThrow(PlatformAccessCatalogService::notFound);}
    private PlatformAccessGuideVersion requireGuideForUpdate(UUID org,UUID id){return versions.findForUpdate(org,id).orElseThrow(PlatformAccessCatalogService::notFound);}
    private void requireActiveType(UUID org,UUID id){if(requireType(org,id).getStatus()!=PlatformAccessTypeStatus.ACTIVE)throw new ApiException(HttpStatus.CONFLICT,"ACCESS_TYPE_ARCHIVED","Archived access types cannot be changed.");}
    private static void requireVersion(long actual,long expected){if(actual!=expected)throw new ApiException(HttpStatus.CONFLICT,"VERSION_CONFLICT","The resource changed. Refresh and try again.");}
    private static int safeSize(int size){return Math.min(Math.max(size,1),MAX_PAGE_SIZE);} private static String safe(String value){return value==null?"":value;}
    private static ApiException notFound(){return new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","Requested platform access resource was not found.");}
    private static ApiException invalid(String message){return new ApiException(HttpStatus.BAD_REQUEST,"PLATFORM_ACCESS_INVALID",message);}
    public record PageResult<T>(List<T> items,int page,int size,long totalElements,int totalPages){public PageResult{items=List.copyOf(items);}}
    public record PublishedGuide(PlatformAccessType type,PlatformAccessGuideVersion version){}
}
