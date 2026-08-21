package com.brainserve.onboarding.contracts.application.service;

import com.brainserve.onboarding.audit.application.service.AuditService;
import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.common.security.TenantPrincipal;
import com.brainserve.onboarding.contracts.api.request.CreateContractTemplateRequest;
import com.brainserve.onboarding.contracts.api.request.UpdateContractTemplateRequest;
import com.brainserve.onboarding.contracts.api.request.UpdateContractTemplateVersionRequest;
import com.brainserve.onboarding.contracts.api.response.ContractTemplateResponse;
import com.brainserve.onboarding.contracts.api.response.ContractTemplateVersionResponse;
import com.brainserve.onboarding.contracts.domain.model.ContractTemplate;
import com.brainserve.onboarding.contracts.domain.model.ContractTemplateStatus;
import com.brainserve.onboarding.contracts.domain.model.ContractTemplateVersion;
import com.brainserve.onboarding.contracts.domain.model.ContractTemplateVersionStatus;
import com.brainserve.onboarding.contracts.infrastructure.persistence.ContractTemplateRepository;
import com.brainserve.onboarding.contracts.infrastructure.persistence.ContractTemplateVersionRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContractTemplateService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final Pattern VARIABLE = Pattern.compile("\\{\\{([a-z_]+)}}");
    private static final Set<String> ALLOWED_VARIABLES = Set.of("organization_name", "client_name", "project_name");
    private final ContractTemplateRepository templates;
    private final ContractTemplateVersionRepository versions;
    private final AuditService audit;
    private final Clock clock;

    public ContractTemplateService(ContractTemplateRepository templates, ContractTemplateVersionRepository versions,
                                   AuditService audit, Clock clock) {
        this.templates = templates; this.versions = versions; this.audit = audit; this.clock = clock;
    }

    @Transactional
    public ContractTemplateResponse create(TenantPrincipal principal, CreateContractTemplateRequest request, HttpServletRequest servletRequest) {
        validateLegalContent(request.legalContent());
        var now = clock.instant();
        UUID templateId = UUID.randomUUID();
        ContractTemplate template = new ContractTemplate(templateId, principal.organizationId(), request.name(), request.description(), principal.userId(), now);
        templates.saveAndFlush(template);
        ContractTemplateVersion version = new ContractTemplateVersion(UUID.randomUUID(), principal.organizationId(), templateId, 1,
                request.title(), request.legalContent(), principal.userId(), now);
        versions.saveAndFlush(version);
        audit.record(principal.organizationId(), principal.userId(), "CONTRACT_TEMPLATE_CREATED", "CONTRACT_TEMPLATE", templateId,
                null, Map.of("name", template.getName(), "draftVersion", 1), servletRequest);
        return detail(principal, templateId);
    }

    @Transactional(readOnly = true)
    public PageResult<ContractTemplateResponse> list(TenantPrincipal principal, int page, int size) {
        var result = templates.findAllByOrganizationId(principal.organizationId(), PageRequest.of(Math.max(page,0), safeSize(size),
                Sort.by(Sort.Direction.DESC,"updatedAt").and(Sort.by(Sort.Direction.DESC,"id"))));
        return new PageResult<>(result.getContent().stream().map(t -> summary(t, List.of())).toList(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public ContractTemplateResponse detail(TenantPrincipal principal, UUID templateId) {
        ContractTemplate t = templates.findByOrganizationIdAndId(principal.organizationId(), templateId).orElseThrow(ContractTemplateService::notFound);
        List<ContractTemplateVersionResponse> values = versions.findAllByOrganizationIdAndTemplateIdOrderByVersionNumberDesc(principal.organizationId(), templateId).stream().map(ContractTemplateService::mapVersion).toList();
        return summary(t, values);
    }

    @Transactional
    public ContractTemplateResponse update(TenantPrincipal principal, UUID templateId, UpdateContractTemplateRequest request, HttpServletRequest servletRequest) {
        ContractTemplate t = templates.findForUpdate(principal.organizationId(), templateId).orElseThrow(ContractTemplateService::notFound);
        requireVersion(t.getVersion(), request.version());
        try { t.update(request.name(), request.description(), principal.userId(), clock.instant()); }
        catch (IllegalStateException|IllegalArgumentException ex) { throw invalid(ex.getMessage()); }
        templates.saveAndFlush(t);
        audit.record(principal.organizationId(), principal.userId(), "CONTRACT_TEMPLATE_UPDATED", "CONTRACT_TEMPLATE", templateId, null, Map.of("name",t.getName()), servletRequest);
        return detail(principal,templateId);
    }

    @Transactional
    public ContractTemplateResponse archive(TenantPrincipal principal, UUID templateId, long expectedVersion, HttpServletRequest servletRequest) {
        ContractTemplate t=templates.findForUpdate(principal.organizationId(),templateId).orElseThrow(ContractTemplateService::notFound);requireVersion(t.getVersion(),expectedVersion);t.archive(principal.userId(),clock.instant());templates.saveAndFlush(t);audit.record(principal.organizationId(),principal.userId(),"CONTRACT_TEMPLATE_ARCHIVED","CONTRACT_TEMPLATE",templateId,null,Map.of("status",t.getStatus()),servletRequest);return detail(principal,templateId);
    }

    @Transactional
    public ContractTemplateVersionResponse createVersion(TenantPrincipal principal,UUID templateId,HttpServletRequest servletRequest){
        ContractTemplate t=templates.findForUpdate(principal.organizationId(),templateId).orElseThrow(ContractTemplateService::notFound);if(t.getStatus()==ContractTemplateStatus.ARCHIVED)throw invalid("Archived templates cannot receive new versions.");
        ContractTemplateVersion draft=versions.findByOrganizationIdAndTemplateIdAndStatus(principal.organizationId(),templateId,ContractTemplateVersionStatus.DRAFT).orElse(null);if(draft!=null)return mapVersion(draft);
        ContractTemplateVersion latest=versions.findFirstByOrganizationIdAndTemplateIdOrderByVersionNumberDesc(principal.organizationId(),templateId).orElseThrow(ContractTemplateService::notFound);
        ContractTemplateVersion next=new ContractTemplateVersion(UUID.randomUUID(),principal.organizationId(),templateId,latest.getVersionNumber()+1,latest.getTitle(),latest.getLegalContent(),principal.userId(),clock.instant());versions.saveAndFlush(next);audit.record(principal.organizationId(),principal.userId(),"CONTRACT_TEMPLATE_VERSION_CREATED","CONTRACT_TEMPLATE_VERSION",next.getId(),null,Map.of("templateId",templateId,"versionNumber",next.getVersionNumber()),servletRequest);return mapVersion(next);
    }

    @Transactional
    public ContractTemplateVersionResponse updateVersion(TenantPrincipal principal,UUID versionId,UpdateContractTemplateVersionRequest request,HttpServletRequest servletRequest){ContractTemplateVersion v=versions.findForUpdate(principal.organizationId(),versionId).orElseThrow(ContractTemplateService::notFound);requireVersion(v.getVersion(),request.version());validateLegalContent(request.legalContent());try{v.replace(request.title(),request.legalContent(),principal.userId(),clock.instant());}catch(IllegalStateException|IllegalArgumentException ex){throw invalid(ex.getMessage());}versions.saveAndFlush(v);audit.record(principal.organizationId(),principal.userId(),"CONTRACT_TEMPLATE_VERSION_UPDATED","CONTRACT_TEMPLATE_VERSION",versionId,null,Map.of("versionNumber",v.getVersionNumber(),"contentHash",v.getContentHash()),servletRequest);return mapVersion(v);}

    @Transactional
    public ContractTemplateVersionResponse publish(TenantPrincipal principal,UUID versionId,long expectedVersion,HttpServletRequest servletRequest){ContractTemplateVersion v=versions.findForUpdate(principal.organizationId(),versionId).orElseThrow(ContractTemplateService::notFound);requireVersion(v.getVersion(),expectedVersion);validateLegalContent(v.getLegalContent());try{v.publish(principal.userId(),clock.instant());}catch(IllegalStateException ex){throw invalid(ex.getMessage());}versions.saveAndFlush(v);audit.record(principal.organizationId(),principal.userId(),"CONTRACT_TEMPLATE_VERSION_PUBLISHED","CONTRACT_TEMPLATE_VERSION",versionId,null,Map.of("templateId",v.getTemplateId(),"versionNumber",v.getVersionNumber(),"contentHash",v.getContentHash()),servletRequest);return mapVersion(v);}

    @Transactional(readOnly = true)
    public ContractTemplateVersionResponse version(TenantPrincipal principal,UUID versionId){return mapVersion(requirePublishedOrDraft(principal.organizationId(),versionId));}

    @Transactional(readOnly = true)
    public List<ContractTemplateVersionResponse> publishedVersions(TenantPrincipal principal) {
        return versions.findAllByOrganizationIdAndStatusOrderByPublishedAtDescIdDesc(
                principal.organizationId(), ContractTemplateVersionStatus.PUBLISHED).stream()
                .map(ContractTemplateService::mapVersion).toList();
    }

    public ContractTemplateVersion requirePublished(UUID organizationId,UUID versionId){ContractTemplateVersion v=versions.findByOrganizationIdAndId(organizationId,versionId).orElseThrow(ContractTemplateService::notFound);if(v.getStatus()!=ContractTemplateVersionStatus.PUBLISHED)throw new ApiException(HttpStatus.CONFLICT,"CONTRACT_TEMPLATE_VERSION_NOT_PUBLISHED","Contract instances require a published legal template version.");return v;}
    private ContractTemplateVersion requirePublishedOrDraft(UUID org,UUID id){return versions.findByOrganizationIdAndId(org,id).orElseThrow(ContractTemplateService::notFound);}

    static void validateLegalContent(String content){if(content==null||content.isBlank())throw invalid("Legal content is required.");Matcher m=VARIABLE.matcher(content);while(m.find())if(!ALLOWED_VARIABLES.contains(m.group(1)))throw invalid("Unsupported contract variable {{"+m.group(1)+"}}.");}
    static ContractTemplateVersionResponse mapVersion(ContractTemplateVersion v){return new ContractTemplateVersionResponse(v.getId(),v.getVersionNumber(),v.getStatus(),v.getTitle(),v.getLegalContent(),v.getContentHash(),v.getPublishedAt(),v.getCreatedAt(),v.getUpdatedAt(),v.getVersion());}
    static ContractTemplateResponse summary(ContractTemplate t,List<ContractTemplateVersionResponse> versions){return new ContractTemplateResponse(t.getId(),t.getName(),t.getDescription(),t.getStatus(),t.getCreatedAt(),t.getUpdatedAt(),t.getVersion(),versions);}
    private static int safeSize(int s){return Math.min(Math.max(s,1),MAX_PAGE_SIZE);}private static void requireVersion(long actual,long expected){if(actual!=expected)throw new ApiException(HttpStatus.CONFLICT,"VERSION_CONFLICT","The contract template changed. Refresh and try again.");}private static ApiException invalid(String m){return new ApiException(HttpStatus.BAD_REQUEST,"CONTRACT_TEMPLATE_INVALID",m);}private static ApiException notFound(){return new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","Requested contract template was not found.");}
    public record PageResult<T>(List<T> items,int page,int size,long totalElements,int totalPages){public PageResult{items=List.copyOf(items);}}
}
