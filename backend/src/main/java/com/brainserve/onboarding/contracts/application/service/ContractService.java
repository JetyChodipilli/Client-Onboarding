package com.brainserve.onboarding.contracts.application.service;

import com.brainserve.onboarding.audit.application.service.AuditService;
import com.brainserve.onboarding.client.application.service.ClientContactLookupService;
import com.brainserve.onboarding.client.application.service.ClientLookupService;
import com.brainserve.onboarding.client.application.service.ClientPortalAccessService;
import com.brainserve.onboarding.common.activity.ActivityTimelineService;
import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.common.event.OutboxService;
import com.brainserve.onboarding.common.security.ClientPrincipal;
import com.brainserve.onboarding.common.security.TenantPrincipal;
import com.brainserve.onboarding.contracts.api.request.CreateContractRequest;
import com.brainserve.onboarding.contracts.api.response.*;
import com.brainserve.onboarding.contracts.domain.model.*;
import com.brainserve.onboarding.contracts.infrastructure.persistence.*;
import com.brainserve.onboarding.contracts.infrastructure.provider.ESignatureProvider;
import com.brainserve.onboarding.contracts.infrastructure.provider.ESignatureProviderRegistry;
import com.brainserve.onboarding.contracts.infrastructure.storage.ContractDocumentStorage;
import com.brainserve.onboarding.onboarding.application.service.OnboardingStepAccessService;
import com.brainserve.onboarding.onboarding.application.service.OnboardingStepCommandService;
import com.brainserve.onboarding.onboarding.domain.model.OnboardingStepStatus;
import com.brainserve.onboarding.organization.application.service.OrganizationLookupService;
import com.brainserve.onboarding.project.application.service.ProjectWorkflowAccessService;
import com.brainserve.onboarding.project.domain.model.ProjectStatus;
import com.brainserve.onboarding.workflow.domain.model.WorkflowStepType;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Contract application boundary. Legal snapshots are immutable and provider I/O is kept outside DB transactions. */
@Service
public class ContractService {
    private static final int MAX_PAGE_SIZE = 100;
    private final ContractRepository contracts;
    private final ContractVersionRepository versions;
    private final ContractRecipientRepository recipients;
    private final ContractSignatureRepository signatures;
    private final ContractTemplateService templates;
    private final ProjectWorkflowAccessService projects;
    private final ClientContactLookupService contacts;
    private final ClientLookupService clients;
    private final ClientPortalAccessService clientAccess;
    private final OrganizationLookupService organizations;
    private final OnboardingStepAccessService stepAccess;
    private final OnboardingStepCommandService stepCommands;
    private final ESignatureProviderRegistry providers;
    private final ContractDocumentStorage storage;
    private final ActivityTimelineService activity;
    private final AuditService audit;
    private final OutboxService outbox;
    private final Clock clock;
    private final TransactionTemplate tx;

    public ContractService(ContractRepository contracts, ContractVersionRepository versions,
                           ContractRecipientRepository recipients, ContractSignatureRepository signatures,
                           ContractTemplateService templates, ProjectWorkflowAccessService projects,
                           ClientContactLookupService contacts, ClientLookupService clients,
                           ClientPortalAccessService clientAccess, OrganizationLookupService organizations,
                           OnboardingStepAccessService stepAccess, OnboardingStepCommandService stepCommands,
                           ESignatureProviderRegistry providers, ContractDocumentStorage storage,
                           ActivityTimelineService activity, AuditService audit, OutboxService outbox,
                           Clock clock, TransactionTemplate tx) {
        this.contracts=contracts;this.versions=versions;this.recipients=recipients;this.signatures=signatures;
        this.templates=templates;this.projects=projects;this.contacts=contacts;this.clients=clients;
        this.clientAccess=clientAccess;this.organizations=organizations;this.stepAccess=stepAccess;this.stepCommands=stepCommands;
        this.providers=providers;this.storage=storage;this.activity=activity;this.audit=audit;this.outbox=outbox;this.clock=clock;this.tx=tx;
    }

    public ContractDetailResponse create(TenantPrincipal principal, String idempotencyKey, CreateContractRequest request,
                                         HttpServletRequest servletRequest) {
        String key = key(idempotencyKey);
        return requireResult(tx.execute(status -> createTx(principal, key, request, servletRequest)));
    }

    private ContractDetailResponse createTx(TenantPrincipal principal, String key, CreateContractRequest request,
                                            HttpServletRequest servletRequest) {
        UUID org = principal.organizationId();
        var project = projects.require(org, request.projectId());
        if (Set.of(ProjectStatus.COMPLETED, ProjectStatus.CANCELLED, ProjectStatus.ARCHIVED).contains(project.status())) {
            throw conflict("PROJECT_STATE_INVALID", "Closed projects cannot receive new contracts.");
        }
        Contract existing = contracts.findByOrganizationIdAndCreationIdempotencyKey(org, key).orElse(null);
        ResolvedTemplate resolved = resolveTemplate(org, project.id(), request.stepId(), request.templateVersionId());
        String subject = blank(request.subject()) ? resolved.version().getTitle() : request.subject().trim();
        List<ClientContactLookupService.ContactRef> contactRefs = resolveContacts(org, project.clientId(), request.recipientContactIds());
        if (existing != null) {
            if (!sameCreate(existing, resolved.version().getId(), request.stepId(), subject, contactRefs)) {
                throw conflict("IDEMPOTENCY_KEY_REUSED", "This Idempotency-Key was already used for a different contract request.");
            }
            return detailInternal(org, existing.getId());
        }
        var orgRef = organizations.require(org);
        var clientRef = clients.requireUsable(org, project.clientId());
        String legal = render(resolved.version().getLegalContent(), orgRef.name(), clientRef.name(), project.name());
        Instant now = clock.instant();
        UUID contractId = UUID.randomUUID();
        Contract contract = new Contract(contractId, org, project.id(), project.clientId(), resolved.onboardingId(), request.stepId(),
                resolved.version().getTemplateId(), resolved.version().getId(), subject, request.expiresAt(), key, principal.userId(), now);
        contract.generated(principal.userId(), now);
        contracts.saveAndFlush(contract);
        ContractVersion snapshot = new ContractVersion(UUID.randomUUID(), org, contractId, project.id(), 1,
                resolved.version().getId(), resolved.version().getTitle(), legal, principal.userId(), now);
        versions.saveAndFlush(snapshot);
        int order=0;
        for (var contact : contactRefs) {
            recipients.save(new ContractRecipient(UUID.randomUUID(), org, contractId, project.clientId(), contact.id(),
                    contact.displayName(), contact.email(), order++, principal.userId(), now));
        }
        recipients.flush();
        if (resolved.stepStatus() == OnboardingStepStatus.AVAILABLE) {
            stepCommands.transition(org, resolved.onboardingId(), request.stepId(), OnboardingStepStatus.IN_PROGRESS, principal.userId());
        }
        activity.record(org, project.clientId(), project.id(), principal.userId(), "CONTRACT_GENERATED", "CONTRACT", contractId,
                "Contract generated", Map.of("subject", subject, "recipientCount", contactRefs.size()));
        audit.record(org, principal.userId(), "CONTRACT_GENERATED", "CONTRACT", contractId, null,
                Map.of("projectId", project.id(), "templateVersionId", resolved.version().getId(), "contentHash", snapshot.getContentHash()), servletRequest);
        outbox.record(org, "CONTRACT_GENERATED", "CONTRACT", contractId,
                Map.of("contractId", contractId, "projectId", project.id(), "clientId", project.clientId()));
        return detailInternal(org, contractId);
    }

    public ContractDetailResponse send(TenantPrincipal principal, UUID contractId, long expectedVersion, String idempotencyKey,
                                       HttpServletRequest servletRequest) {
        String key = key(idempotencyKey);
        PreparedSend prepared = requireResult(tx.execute(status -> prepareSend(principal, contractId, expectedVersion, key)));
        if (prepared.alreadySent()) return detailInternal(principal.organizationId(), contractId);
        ESignatureProvider provider = providers.byCode(prepared.provider());
        ESignatureProvider.SendResult external;
        try {
            external = provider.send(new ESignatureProvider.SendCommand(principal.organizationId(), prepared.projectId(), contractId,
                    prepared.contractVersionId(), key, prepared.title(), prepared.legalContent(), prepared.recipients(), prepared.expiresAt()));
        } catch (RuntimeException ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CONTRACT_PROVIDER_UNAVAILABLE",
                    "The e-signature provider could not create the signing request. Retry with the same Idempotency-Key.");
        }
        validateSendResult(prepared, external);
        return requireResult(tx.execute(status -> finalizeSend(principal, contractId, key, prepared.provider(), external, servletRequest)));
    }

    private PreparedSend prepareSend(TenantPrincipal principal, UUID contractId, long expectedVersion, String key) {
        Contract contract=contracts.findForUpdate(principal.organizationId(),contractId).orElseThrow(ContractService::notFound);
        if (contract.getStatus()==ContractStatus.SENT && key.equals(contract.getSendIdempotencyKey())) {
            return new PreparedSend(true, contract.getProjectId(), null, null, null, contract.getExpiresAt(), List.of(), contract.getProvider());
        }
        requireVersion(contract.getVersion(),expectedVersion);
        if (contract.getStatus()!=ContractStatus.GENERATED) throw conflict("CONTRACT_STATE_INVALID","Only generated contracts can be sent.");
        try { contract.reserveSend(key,principal.userId(),clock.instant()); }
        catch (IllegalStateException ex) { throw conflict("CONTRACT_SEND_IN_PROGRESS",ex.getMessage()); }
        contracts.saveAndFlush(contract);
        ContractVersion version=latestVersion(principal.organizationId(),contractId);
        List<ContractRecipient> values=recipients.findAllByOrganizationIdAndContractIdOrderBySigningOrderAscIdAsc(principal.organizationId(),contractId);
        if(values.isEmpty())throw invalid("CONTRACT_RECIPIENT_REQUIRED","A contract requires at least one recipient.");
        var provider=providers.current();
        List<ESignatureProvider.Recipient> providerRecipients=values.stream().map(r->new ESignatureProvider.Recipient(r.getId().toString(),r.getDisplayName(),r.getEmail(),r.getSigningOrder())).toList();
        return new PreparedSend(false,contract.getProjectId(),version.getId(),version.getTitle(),version.getLegalContent(),contract.getExpiresAt(),providerRecipients,provider.providerCode());
    }

    private ContractDetailResponse finalizeSend(TenantPrincipal principal, UUID contractId, String key, String providerCode,
                                                ESignatureProvider.SendResult result, HttpServletRequest servletRequest) {
        Contract contract=contracts.findForUpdate(principal.organizationId(),contractId).orElseThrow(ContractService::notFound);
        if(contract.getStatus()==ContractStatus.SENT && key.equals(contract.getSendIdempotencyKey()))return detailInternal(principal.organizationId(),contractId);
        if(contract.getStatus()!=ContractStatus.GENERATED||!key.equals(contract.getSendIdempotencyKey()))throw conflict("CONTRACT_SEND_CONFLICT","The contract changed while the provider request was being created.");
        Instant now=clock.instant();
        ESignatureProvider provider=providers.byCode(providerCode);
        try{contract.sent(provider.providerCode(),result.providerDocumentId(),result.signingUrl(),key,now,contract.getExpiresAt(),principal.userId());}
        catch(IllegalStateException ex){throw conflict("CONTRACT_STATE_INVALID",ex.getMessage());}
        contracts.saveAndFlush(contract);
        ContractVersion version=latestVersion(principal.organizationId(),contractId);version.markSent(result.providerDocumentId(),now);versions.saveAndFlush(version);
        for(ContractRecipient recipient:recipients.findAllByOrganizationIdAndContractIdOrderBySigningOrderAscIdAsc(principal.organizationId(),contractId)){
            recipient.sent(result.recipientIds().get(recipient.getNormalizedEmail()));recipients.save(recipient);
        }
        recipients.flush();
        activity.record(principal.organizationId(),contract.getClientId(),contract.getProjectId(),principal.userId(),"CONTRACT_SENT","CONTRACT",contractId,"Contract sent for e-signature",Map.of("provider",provider.providerCode()));
        audit.record(principal.organizationId(),principal.userId(),"CONTRACT_SENT","CONTRACT",contractId,null,Map.of("provider",provider.providerCode(),"providerDocumentId",result.providerDocumentId()),servletRequest);
        outbox.record(principal.organizationId(),"CONTRACT_SENT","CONTRACT",contractId,Map.of("contractId",contractId,"projectId",contract.getProjectId(),"clientId",contract.getClientId()));
        return detailInternal(principal.organizationId(),contractId);
    }

    public ContractDetailResponse cancel(TenantPrincipal principal, UUID contractId, long expectedVersion, String reason,
                                         HttpServletRequest servletRequest) {
        return close(principal,contractId,expectedVersion,reason,false,servletRequest);
    }
    public ContractDetailResponse voidContract(TenantPrincipal principal, UUID contractId, long expectedVersion, String reason,
                                               HttpServletRequest servletRequest) {
        return close(principal,contractId,expectedVersion,reason,true,servletRequest);
    }
    private ContractDetailResponse close(TenantPrincipal principal,UUID contractId,long expectedVersion,String reason,boolean voided,HttpServletRequest req){
        String why=requiredText(reason,"A reason is required",1000);
        ClosePrepared prepared=requireResult(tx.execute(status->{Contract c=contracts.findForUpdate(principal.organizationId(),contractId).orElseThrow(ContractService::notFound);requireVersion(c.getVersion(),expectedVersion);if(c.getStatus()==ContractStatus.SIGNED)throw conflict("CONTRACT_STATE_INVALID","Signed contracts cannot be cancelled or voided.");return new ClosePrepared(c.getStatus(),c.getProvider(),c.getProviderDocumentId());}));
        if(prepared.providerDocumentId()!=null){try{providers.byCode(prepared.provider()).voidDocument(prepared.providerDocumentId(),why);}catch(RuntimeException ex){throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"CONTRACT_PROVIDER_UNAVAILABLE","The e-signature provider could not void the document. No local state was changed.");}}
        return requireResult(tx.execute(status->{Contract c=contracts.findForUpdate(principal.organizationId(),contractId).orElseThrow(ContractService::notFound);if(c.getStatus()==ContractStatus.SIGNED)throw conflict("CONTRACT_STATE_INVALID","The contract was signed before the close operation completed.");if(c.getStatus()==ContractStatus.CANCELLED||c.getStatus()==ContractStatus.VOID)return detailInternal(principal.organizationId(),c.getId());try{if(voided)c.voidContract(principal.userId(),clock.instant());else c.cancel(principal.userId(),clock.instant());}catch(IllegalStateException ex){throw conflict("CONTRACT_STATE_INVALID",ex.getMessage());}contracts.saveAndFlush(c);String action=voided?"CONTRACT_VOIDED":"CONTRACT_CANCELLED";activity.record(principal.organizationId(),c.getClientId(),c.getProjectId(),principal.userId(),action,"CONTRACT",c.getId(),voided?"Contract voided":"Contract cancelled",Map.of("reason",why));audit.record(principal.organizationId(),principal.userId(),action,"CONTRACT",c.getId(),null,Map.of("reason",why),req);outbox.record(principal.organizationId(),action,"CONTRACT",c.getId(),Map.of("contractId",c.getId(),"projectId",c.getProjectId(),"reason",why));return detailInternal(principal.organizationId(),c.getId());}));
    }

    public PageResult<ContractSummaryResponse> list(TenantPrincipal principal, UUID projectId, ContractStatus status, int page, int size){
        return listInternal(principal.organizationId(),projectId,status,page,size);
    }
    public ContractDetailResponse detail(TenantPrincipal principal,UUID contractId){return detailInternal(principal.organizationId(),contractId);}

    public PageResult<ClientContractSummaryResponse> clientList(ClientPrincipal principal,UUID projectId,int page,int size){
        clientAccess.requireProjectActionAccess(principal.organizationId(),principal.userId(),projectId);
        Page<Contract> result=contracts.findAllByOrganizationIdAndProjectIdAndStatusIn(principal.organizationId(),projectId,
                List.of(ContractStatus.SENT,ContractStatus.VIEWED,ContractStatus.SIGNED,ContractStatus.DECLINED,ContractStatus.EXPIRED),
                PageRequest.of(Math.max(0,page),safeSize(size),Sort.by(Sort.Direction.DESC,"createdAt").and(Sort.by(Sort.Direction.DESC,"id"))));
        List<ClientContractSummaryResponse> mapped=result.getContent().stream().map(c->new ClientContractSummaryResponse(c.getId(),c.getProjectId(),c.getSubject(),c.getStatus(),c.getExpiresAt(),c.getSentAt(),c.getSignedAt(),isRecipient(principal.email(),principal.organizationId(),c.getId(),c.getStatus()))).toList();
        return new PageResult<>(mapped,result.getNumber(),result.getSize(),result.getTotalElements(),result.getTotalPages());
    }

    public ClientContractDetailResponse clientDetail(ClientPrincipal principal,UUID projectId,UUID contractId,boolean markViewed,HttpServletRequest request){
        clientAccess.requireProjectActionAccess(principal.organizationId(),principal.userId(),projectId);
        if(markViewed){return requireResult(tx.execute(status->clientDetailTx(principal,projectId,contractId,request)));}
        return clientDetailInternal(principal,projectId,contractId);
    }

    private ClientContractDetailResponse clientDetailTx(ClientPrincipal principal,UUID projectId,UUID contractId,HttpServletRequest req){
        Contract contract=contracts.findForUpdate(principal.organizationId(),contractId).filter(c->c.getProjectId().equals(projectId)).orElseThrow(ContractService::notFound);
        requireClientVisible(contract);
        ContractRecipient recipient=recipients.findByOrganizationIdAndContractIdAndNormalizedEmail(principal.organizationId(),contractId,normalizeEmail(principal.email())).orElse(null);
        if(recipient!=null&&contract.getStatus()==ContractStatus.SENT){contract.markViewed(principal.userId(),clock.instant());recipient.viewed(clock.instant());contracts.saveAndFlush(contract);recipients.saveAndFlush(recipient);audit.recordClient(principal.organizationId(),principal.userId(),"CONTRACT_VIEWED","CONTRACT",contractId,null,Map.of("projectId",projectId),req);}
        return clientDetailInternal(principal,projectId,contractId);
    }

    public ContractDownloadResponse internalDownload(TenantPrincipal principal,UUID contractId){return download(principal.organizationId(),contractId,null);}
    public ContractDownloadResponse clientDownload(ClientPrincipal principal,UUID projectId,UUID contractId){clientAccess.requireProjectActionAccess(principal.organizationId(),principal.userId(),projectId);return download(principal.organizationId(),contractId,projectId);}

    private ContractDownloadResponse download(UUID org,UUID contractId,UUID expectedProject){
        Contract c=contracts.findByOrganizationIdAndId(org,contractId).orElseThrow(ContractService::notFound);if(expectedProject!=null&&!c.getProjectId().equals(expectedProject))throw notFound();if(c.getStatus()!=ContractStatus.SIGNED)throw conflict("CONTRACT_NOT_SIGNED","The signed document is not available yet.");ContractVersion v=latestVersion(org,contractId);if(v.getSignedDocumentKey()==null||!storage.available())throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"CONTRACT_DOCUMENT_UNAVAILABLE","The signed contract document is temporarily unavailable.");var op=storage.presignDownload(v.getSignedDocumentBucket(),v.getSignedDocumentKey(),safeFilename(c.getSubject()));return new ContractDownloadResponse(op.url(),op.expiresAt(),safeFilename(c.getSubject()));
    }

    private PageResult<ContractSummaryResponse> listInternal(UUID org,UUID projectId,ContractStatus status,int page,int size){
        var pageable=PageRequest.of(Math.max(0,page),safeSize(size),Sort.by(Sort.Direction.DESC,"createdAt").and(Sort.by(Sort.Direction.DESC,"id")));Page<Contract> result;if(projectId!=null){projects.require(org,projectId);result=status==null?contracts.findAllByOrganizationIdAndProjectId(org,projectId,pageable):contracts.findAllByOrganizationIdAndProjectIdAndStatusIn(org,projectId,List.of(status),pageable);}else{result=contracts.findAllByOrganizationId(org,pageable);}return new PageResult<>(result.getContent().stream().map(ContractService::summary).toList(),result.getNumber(),result.getSize(),result.getTotalElements(),result.getTotalPages());
    }

    ContractDetailResponse detailInternal(UUID org,UUID contractId){
        Contract c=contracts.findByOrganizationIdAndId(org,contractId).orElseThrow(ContractService::notFound);ContractVersion v=latestVersion(org,contractId);List<ContractRecipientResponse> r=recipients.findAllByOrganizationIdAndContractIdOrderBySigningOrderAscIdAsc(org,contractId).stream().map(ContractService::recipient).toList();List<ContractSignatureResponse> s=signatures.findAllByOrganizationIdAndContractIdOrderBySignedAtAscIdAsc(org,contractId).stream().map(ContractService::signature).toList();return new ContractDetailResponse(c.getId(),c.getProjectId(),c.getClientId(),c.getOnboardingId(),c.getStepInstanceId(),c.getTemplateId(),c.getTemplateVersionId(),c.getSubject(),c.getStatus(),c.getProvider(),c.getProviderDocumentId(),c.getProviderSigningUrl(),c.getExpiresAt(),c.getGeneratedAt(),c.getSentAt(),c.getViewedAt(),c.getSignedAt(),c.getDeclinedAt(),c.getCreatedAt(),c.getVersion(),version(v),r,s);
    }

    private ClientContractDetailResponse clientDetailInternal(ClientPrincipal principal,UUID projectId,UUID contractId){
        Contract c=contracts.findByOrganizationIdAndId(principal.organizationId(),contractId).filter(x->x.getProjectId().equals(projectId)).orElseThrow(ContractService::notFound);requireClientVisible(c);ContractVersion v=latestVersion(principal.organizationId(),contractId);List<ContractRecipientResponse> r=recipients.findAllByOrganizationIdAndContractIdOrderBySigningOrderAscIdAsc(principal.organizationId(),contractId).stream().map(ContractService::recipient).toList();boolean recipient=isRecipient(principal.email(),principal.organizationId(),contractId,c.getStatus());String url=recipient&&(c.getStatus()==ContractStatus.SENT||c.getStatus()==ContractStatus.VIEWED)?c.getProviderSigningUrl():null;return new ClientContractDetailResponse(c.getId(),c.getProjectId(),c.getSubject(),c.getStatus(),v.getTitle(),v.getLegalContent(),v.getContentHash(),c.getExpiresAt(),c.getSentAt(),c.getViewedAt(),c.getSignedAt(),url,c.getStatus()==ContractStatus.SIGNED&&v.getSignedDocumentKey()!=null,r,c.getVersion());
    }


    private static void validateSendResult(PreparedSend prepared, ESignatureProvider.SendResult result) {
        if (result == null || blank(result.providerDocumentId()) || result.providerDocumentId().length() > 180) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "CONTRACT_PROVIDER_RESPONSE_INVALID",
                    "The e-signature provider returned an invalid document identifier.");
        }
        requireWebUrl(result.signingUrl());
        Map<String,String> ids = result.recipientIds();
        for (ESignatureProvider.Recipient recipient : prepared.recipients()) {
            String providerRecipientId = ids.get(normalizeEmail(recipient.email()));
            if (blank(providerRecipientId) || providerRecipientId.length() > 180) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "CONTRACT_PROVIDER_RESPONSE_INVALID",
                        "The e-signature provider did not return an identity for every recipient.");
            }
        }
    }

    private static void requireWebUrl(String value) {
        if (blank(value) || value.length() > 2000) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "CONTRACT_PROVIDER_RESPONSE_INVALID",
                    "The e-signature provider returned an invalid signing URL.");
        }
        try {
            java.net.URI uri = java.net.URI.create(value);
            String scheme = uri.getScheme();
            if (uri.getHost() == null || !("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))) throw new IllegalArgumentException();
            if ("http".equalsIgnoreCase(scheme) && !("localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost()))) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "CONTRACT_PROVIDER_RESPONSE_INVALID",
                    "The e-signature provider returned an unsafe signing URL.");
        }
    }

    private ResolvedTemplate resolveTemplate(UUID org,UUID projectId,UUID stepId,UUID requestedVersion){
        if(stepId==null){if(requestedVersion==null)throw invalid("CONTRACT_TEMPLATE_REQUIRED","templateVersionId is required for a standalone contract.");return new ResolvedTemplate(templates.requirePublished(org,requestedVersion),null,null);}
        var step=stepAccess.requireForProjectForUpdate(org,projectId,stepId);if(step.stepType()!=WorkflowStepType.CONTRACT)throw invalid("CONTRACT_STEP_INVALID","The selected workflow step is not a contract requirement.");if(Set.of(OnboardingStepStatus.LOCKED,OnboardingStepStatus.COMPLETED,OnboardingStepStatus.SKIPPED,OnboardingStepStatus.CANCELLED).contains(step.status()))throw conflict("CONTRACT_STEP_STATE_INVALID","The contract workflow step is not currently actionable.");UUID configured=configuredVersion(step.configuration());if(requestedVersion!=null&&!requestedVersion.equals(configured))throw invalid("CONTRACT_TEMPLATE_MISMATCH","The requested contract template does not match the workflow requirement.");return new ResolvedTemplate(templates.requirePublished(org,configured),step.onboardingId(),step.status());
    }

    private List<ClientContactLookupService.ContactRef> resolveContacts(UUID org,UUID clientId,List<UUID> ids){LinkedHashSet<UUID> unique=new LinkedHashSet<>(ids);if(unique.isEmpty()||unique.size()!=ids.size())throw invalid("CONTRACT_RECIPIENT_INVALID","At least one unique recipient contact is required.");return unique.stream().map(id->contacts.requireForClient(org,clientId,id)).toList();}
    private boolean sameCreate(Contract c,UUID templateVersionId,UUID stepId,String subject,List<ClientContactLookupService.ContactRef> requested){if(!c.getTemplateVersionId().equals(templateVersionId)||!Objects.equals(c.getStepInstanceId(),stepId)||!c.getSubject().equals(subject))return false;Set<UUID> stored=recipients.findAllByOrganizationIdAndContractIdOrderBySigningOrderAscIdAsc(c.getOrganizationId(),c.getId()).stream().map(ContractRecipient::getContactId).collect(Collectors.toSet());Set<UUID> want=requested.stream().map(ClientContactLookupService.ContactRef::id).collect(Collectors.toSet());return stored.equals(want);}
    private static UUID configuredVersion(JsonNode config){try{return UUID.fromString(config.path("contractTemplateVersionId").asText());}catch(RuntimeException ex){throw invalid("CONTRACT_CONFIGURATION_INVALID","The workflow contract configuration is invalid.");}}
    private ContractVersion latestVersion(UUID org,UUID contractId){return versions.findFirstByOrganizationIdAndContractIdOrderByVersionNumberDesc(org,contractId).orElseThrow(ContractService::notFound);}
    private boolean isRecipient(String email,UUID org,UUID contractId,ContractStatus status){return (status==ContractStatus.SENT||status==ContractStatus.VIEWED)&&recipients.findByOrganizationIdAndContractIdAndNormalizedEmail(org,contractId,normalizeEmail(email)).filter(r->r.getStatus()!=ContractRecipientStatus.SIGNED&&r.getStatus()!=ContractRecipientStatus.DECLINED).isPresent();}
    private static void requireClientVisible(Contract c){if(c.getStatus()==ContractStatus.DRAFT||c.getStatus()==ContractStatus.GENERATED||c.getStatus()==ContractStatus.VOID||c.getStatus()==ContractStatus.CANCELLED)throw notFound();}
    private static ContractSummaryResponse summary(Contract c){return new ContractSummaryResponse(c.getId(),c.getProjectId(),c.getSubject(),c.getStatus(),c.getExpiresAt(),c.getSentAt(),c.getSignedAt(),c.getCreatedAt(),c.getVersion());}
    private static ContractRecipientResponse recipient(ContractRecipient r){return new ContractRecipientResponse(r.getId(),r.getContactId(),r.getDisplayName(),r.getEmail(),r.getSigningOrder(),r.getStatus(),r.getViewedAt(),r.getSignedAt(),r.getDeclinedAt());}
    private static ContractSignatureResponse signature(ContractSignature s){return new ContractSignatureResponse(s.getId(),s.getRecipientId(),s.getSignatoryName(),s.getSignatoryEmail(),s.getSignedAt());}
    private static ContractVersionResponse version(ContractVersion v){return new ContractVersionResponse(v.getId(),v.getVersionNumber(),v.getTemplateVersionId(),v.getTitle(),v.getLegalContent(),v.getContentHash(),v.getSentAt(),v.getSignedDocumentKey()!=null,v.getSignedDocumentSha256(),v.getSignedDocumentSize(),v.getSignedDocumentStoredAt());}
    private static String render(String source,String organizationName,String clientName,String projectName){return source.replace("{{organization_name}}",organizationName).replace("{{client_name}}",clientName).replace("{{project_name}}",projectName);}
    private static String safeFilename(String subject){String value=subject==null?"signed-contract":subject.replaceAll("[^A-Za-z0-9._ -]","_").trim();if(value.isBlank())value="signed-contract";if(value.length()>120)value=value.substring(0,120);return value+".pdf";}
    private static String normalizeEmail(String v){return v==null?"":v.trim().toLowerCase(Locale.ROOT);}
    private static String key(String v){if(v==null||!v.matches("[A-Za-z0-9._:-]{8,128}"))throw invalid("IDEMPOTENCY_KEY_INVALID","A valid Idempotency-Key is required.");return v;}
    private static String requiredText(String v,String message,int max){if(v==null||v.isBlank())throw invalid("VALIDATION_ERROR",message);String n=v.trim();if(n.length()>max)throw invalid("VALIDATION_ERROR","Reason is too long.");return n;}
    private static boolean blank(String v){return v==null||v.isBlank();}
    private static int safeSize(int size){return Math.min(Math.max(size,1),MAX_PAGE_SIZE);}
    private static void requireVersion(long actual,long expected){if(actual!=expected)throw conflict("VERSION_CONFLICT","The contract changed. Refresh and try again.");}
    private static <T>T requireResult(T value){if(value==null)throw new IllegalStateException("Transaction returned no result");return value;}
    static ApiException invalid(String code,String message){return new ApiException(HttpStatus.BAD_REQUEST,code,message);}static ApiException conflict(String code,String message){return new ApiException(HttpStatus.CONFLICT,code,message);}static ApiException notFound(){return new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","Requested contract was not found.");}
    public record PageResult<T>(List<T> items,int page,int size,long totalElements,int totalPages){public PageResult{items=List.copyOf(items);}}
    private record ResolvedTemplate(ContractTemplateVersion version,UUID onboardingId,OnboardingStepStatus stepStatus){}
    private record PreparedSend(boolean alreadySent,UUID projectId,UUID contractVersionId,String title,String legalContent,Instant expiresAt,List<ESignatureProvider.Recipient> recipients,String provider){}
    private record ClosePrepared(ContractStatus status,String provider,String providerDocumentId){}
}
