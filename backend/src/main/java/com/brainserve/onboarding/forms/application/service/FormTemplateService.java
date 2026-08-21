package com.brainserve.onboarding.forms.application.service;

import com.brainserve.onboarding.audit.application.service.AuditService;
import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.common.security.TenantPrincipal;
import com.brainserve.onboarding.forms.api.request.CreateFormRequest;
import com.brainserve.onboarding.forms.api.request.CreateFormVersionRequest;
import com.brainserve.onboarding.forms.api.request.SaveFormDraftRequest;
import com.brainserve.onboarding.forms.api.request.UpdateFormRequest;
import com.brainserve.onboarding.forms.api.response.FormDetailResponse;
import com.brainserve.onboarding.forms.api.response.FormFieldResponse;
import com.brainserve.onboarding.forms.api.response.FormSummaryResponse;
import com.brainserve.onboarding.forms.api.response.FormVersionDetailResponse;
import com.brainserve.onboarding.forms.api.response.FormVersionSummaryResponse;
import com.brainserve.onboarding.forms.api.response.PublishedFormVersionResponse;
import com.brainserve.onboarding.forms.domain.model.FormField;
import com.brainserve.onboarding.forms.domain.model.FormStatus;
import com.brainserve.onboarding.forms.domain.model.FormTemplate;
import com.brainserve.onboarding.forms.domain.model.FormTemplateVersion;
import com.brainserve.onboarding.forms.domain.model.FormVersionStatus;
import com.brainserve.onboarding.forms.infrastructure.persistence.FormFieldRepository;
import com.brainserve.onboarding.forms.infrastructure.persistence.FormTemplateRepository;
import com.brainserve.onboarding.forms.infrastructure.persistence.FormTemplateVersionRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FormTemplateService {
    private static final int MAX_PAGE_SIZE = 100;

    private final FormTemplateRepository forms;
    private final FormTemplateVersionRepository versions;
    private final FormFieldRepository fields;
    private final FormDefinitionValidator definitionValidator;
    private final AuditService audit;
    private final Clock clock;

    public FormTemplateService(FormTemplateRepository forms, FormTemplateVersionRepository versions,
                               FormFieldRepository fields, FormDefinitionValidator definitionValidator,
                               AuditService audit, Clock clock) {
        this.forms = forms;
        this.versions = versions;
        this.fields = fields;
        this.definitionValidator = definitionValidator;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public FormDetailResponse create(TenantPrincipal principal, CreateFormRequest request, HttpServletRequest servletRequest) {
        String name = normalizedName(request.name());
        if (forms.existsByOrganizationIdAndNameIgnoreCaseAndStatus(principal.organizationId(), name, FormStatus.ACTIVE)) throw duplicateName();
        Instant now = clock.instant();
        FormTemplate form = forms.saveAndFlush(new FormTemplate(UUID.randomUUID(), principal.organizationId(), name,
                request.description(), principal.userId(), now));
        FormTemplateVersion version = versions.saveAndFlush(new FormTemplateVersion(UUID.randomUUID(), principal.organizationId(),
                form.getId(), 1, request.initialChangeNote(), principal.userId(), now));
        audit.record(principal.organizationId(), principal.userId(), "FORM_CREATED", "FORM", form.getId(), null,
                Map.of("name", form.getName(), "draftVersion", version.getVersionNumber()), servletRequest);
        return detail(form);
    }

    @Transactional(readOnly = true)
    public PageResult<FormSummaryResponse> list(TenantPrincipal principal, int page, int size, boolean includeArchived) {
        var pageable = PageRequest.of(Math.max(page, 0), safeSize(size),
                Sort.by(Sort.Direction.DESC, "updatedAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<FormTemplate> result = includeArchived
                ? forms.findAllByOrganizationId(principal.organizationId(), pageable)
                : forms.findAllByOrganizationIdAndStatus(principal.organizationId(), FormStatus.ACTIVE, pageable);
        Set<UUID> ids = result.getContent().stream().map(FormTemplate::getId).collect(Collectors.toSet());
        Map<UUID, List<FormTemplateVersion>> byForm = ids.isEmpty() ? Map.of()
                : versions.findAllByOrganizationIdAndFormIdIn(principal.organizationId(), ids).stream()
                        .collect(Collectors.groupingBy(FormTemplateVersion::getFormId));
        List<FormSummaryResponse> items = result.getContent().stream()
                .map(form -> summary(form, byForm.getOrDefault(form.getId(), List.of()))).toList();
        return new PageResult<>(items, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public FormDetailResponse get(TenantPrincipal principal, UUID formId) {
        return detail(requireForm(principal.organizationId(), formId));
    }

    @Transactional
    public FormDetailResponse update(TenantPrincipal principal, UUID formId, UpdateFormRequest request, HttpServletRequest servletRequest) {
        FormTemplate form = requireFormForUpdate(principal.organizationId(), formId);
        requireVersion(form.getVersion(), request.version());
        String name = normalizedName(request.name());
        if (forms.existsByOrganizationIdAndNameIgnoreCaseAndStatusAndIdNot(principal.organizationId(), name, FormStatus.ACTIVE, formId)) throw duplicateName();
        Map<String, Object> before = Map.of("name", form.getName(), "description", safe(form.getDescription()), "version", form.getVersion());
        try { form.update(name, request.description(), principal.userId(), clock.instant()); }
        catch (IllegalArgumentException | IllegalStateException ex) { throw invalid(ex.getMessage()); }
        forms.saveAndFlush(form);
        audit.record(principal.organizationId(), principal.userId(), "FORM_UPDATED", "FORM", formId, before,
                Map.of("name", form.getName(), "description", safe(form.getDescription()), "version", form.getVersion()), servletRequest);
        return detail(form);
    }

    @Transactional
    public FormDetailResponse archive(TenantPrincipal principal, UUID formId, long expectedVersion, HttpServletRequest servletRequest) {
        FormTemplate form = requireFormForUpdate(principal.organizationId(), formId);
        requireVersion(form.getVersion(), expectedVersion);
        FormStatus before = form.getStatus();
        form.archive(principal.userId(), clock.instant());
        forms.saveAndFlush(form);
        audit.record(principal.organizationId(), principal.userId(), "FORM_ARCHIVED", "FORM", formId,
                Map.of("status", before), Map.of("status", form.getStatus()), servletRequest);
        return detail(form);
    }

    @Transactional
    public FormVersionDetailResponse createVersion(TenantPrincipal principal, UUID formId, CreateFormVersionRequest request,
                                                   HttpServletRequest servletRequest) {
        FormTemplate form = requireActiveFormForUpdate(principal.organizationId(), formId);
        if (versions.existsByOrganizationIdAndFormIdAndStatus(principal.organizationId(), formId, FormVersionStatus.DRAFT)) {
            throw new ApiException(HttpStatus.CONFLICT, "FORM_DRAFT_EXISTS", "Publish or continue the existing form draft before creating another version.");
        }
        FormTemplateVersion source = versions.findFirstByOrganizationIdAndFormIdAndStatusOrderByVersionNumberDesc(
                principal.organizationId(), formId, FormVersionStatus.PUBLISHED).orElse(null);
        int number = source == null ? 1 : source.getVersionNumber() + 1;
        Instant now = clock.instant();
        FormTemplateVersion target = versions.saveAndFlush(new FormTemplateVersion(UUID.randomUUID(), principal.organizationId(),
                formId, number, request.changeNote(), principal.userId(), now));
        if (source != null) cloneFields(principal, source.getId(), target.getId(), now);
        audit.record(principal.organizationId(), principal.userId(), "FORM_VERSION_CREATED", "FORM_VERSION", target.getId(), null,
                Map.of("formId", form.getId(), "versionNumber", target.getVersionNumber(),
                        "clonedFrom", source == null ? "" : source.getId().toString()), servletRequest);
        return versionDetail(principal.organizationId(), target);
    }

    @Transactional
    public FormVersionDetailResponse saveDraft(TenantPrincipal principal, UUID versionId, SaveFormDraftRequest request,
                                               HttpServletRequest servletRequest) {
        FormTemplateVersion version = versions.findForUpdate(principal.organizationId(), versionId).orElseThrow(FormTemplateService::notFound);
        requireVersion(version.getVersion(), request.version());
        try { version.requireDraft(); } catch (IllegalStateException ex) { throw immutable(); }
        requireActiveForm(principal.organizationId(), version.getFormId());
        List<FormDefinitionValidator.NormalizedField> normalized = definitionValidator.normalize(request.fields());

        fields.deleteAllByOrganizationIdAndFormVersionId(principal.organizationId(), versionId);
        fields.flush();
        Instant now = clock.instant();
        List<FormField> entities = normalized.stream().map(field -> new FormField(UUID.randomUUID(), principal.organizationId(), versionId,
                field.fieldKey(), field.label(), field.helpText(), field.fieldType(), field.displayOrder(), field.required(),
                field.conditionExpression(), field.configuration(), principal.userId(), now)).toList();
        fields.saveAllAndFlush(entities);
        version.revise(request.changeNote(), principal.userId(), now);
        versions.saveAndFlush(version);
        audit.record(principal.organizationId(), principal.userId(), "FORM_DRAFT_SAVED", "FORM_VERSION", versionId, null,
                Map.of("formId", version.getFormId(), "versionNumber", version.getVersionNumber(), "fieldCount", entities.size()), servletRequest);
        return versionDetail(principal.organizationId(), version);
    }

    @Transactional
    public FormVersionDetailResponse publish(TenantPrincipal principal, UUID versionId, long expectedVersion,
                                             HttpServletRequest servletRequest) {
        FormTemplateVersion version = versions.findForUpdate(principal.organizationId(), versionId).orElseThrow(FormTemplateService::notFound);
        requireVersion(version.getVersion(), expectedVersion);
        try { version.requireDraft(); } catch (IllegalStateException ex) { throw immutable(); }
        requireActiveForm(principal.organizationId(), version.getFormId());
        List<FormField> current = fields.findAllByOrganizationIdAndFormVersionIdOrderByDisplayOrderAscIdAsc(principal.organizationId(), versionId);
        definitionValidator.validatePersisted(current);
        version.publish(principal.userId(), clock.instant());
        versions.saveAndFlush(version);
        audit.record(principal.organizationId(), principal.userId(), "FORM_VERSION_PUBLISHED", "FORM_VERSION", versionId, null,
                Map.of("formId", version.getFormId(), "versionNumber", version.getVersionNumber(), "fieldCount", current.size()), servletRequest);
        return versionDetail(principal.organizationId(), version);
    }

    @Transactional(readOnly = true)
    public FormVersionDetailResponse getVersion(TenantPrincipal principal, UUID versionId) {
        return versionDetail(principal.organizationId(), versions.findByOrganizationIdAndId(principal.organizationId(), versionId)
                .orElseThrow(FormTemplateService::notFound));
    }

    @Transactional(readOnly = true)
    public List<PublishedFormVersionResponse> publishedVersions(TenantPrincipal principal) {
        List<FormTemplate> active = forms.findAllByOrganizationId(principal.organizationId(), PageRequest.of(0, MAX_PAGE_SIZE,
                Sort.by(Sort.Direction.ASC, "name"))).getContent().stream().filter(f -> f.getStatus() == FormStatus.ACTIVE).toList();
        if (active.isEmpty()) return List.of();
        Set<UUID> ids = active.stream().map(FormTemplate::getId).collect(Collectors.toSet());
        List<FormTemplateVersion> published = versions.findAllByOrganizationIdAndFormIdIn(principal.organizationId(), ids).stream()
                .filter(v -> v.getStatus() == FormVersionStatus.PUBLISHED)
                .sorted(Comparator.comparing(FormTemplateVersion::getFormId).thenComparing(FormTemplateVersion::getVersionNumber).reversed()).toList();
        Map<UUID, Integer> counts = fieldCounts(principal.organizationId(), published.stream().map(FormTemplateVersion::getId).toList());
        Map<UUID, String> names = active.stream().collect(Collectors.toMap(FormTemplate::getId, FormTemplate::getName));
        return published.stream().map(v -> new PublishedFormVersionResponse(v.getFormId(), names.getOrDefault(v.getFormId(), "Form"),
                v.getId(), v.getVersionNumber(), counts.getOrDefault(v.getId(), 0))).toList();
    }

    private void cloneFields(TenantPrincipal principal, UUID sourceVersionId, UUID targetVersionId, Instant now) {
        List<FormField> source = fields.findAllByOrganizationIdAndFormVersionIdOrderByDisplayOrderAscIdAsc(principal.organizationId(), sourceVersionId);
        List<FormField> clones = source.stream().map(field -> new FormField(UUID.randomUUID(), principal.organizationId(), targetVersionId,
                field.getFieldKey(), field.getLabel(), field.getHelpText(), field.getFieldType(), field.getDisplayOrder(), field.isRequired(),
                field.getConditionExpression(), field.getConfigurationJson(), principal.userId(), now)).toList();
        fields.saveAllAndFlush(clones);
    }

    private FormDetailResponse detail(FormTemplate form) {
        List<FormTemplateVersion> all = versions.findAllByOrganizationIdAndFormIdOrderByVersionNumberDesc(form.getOrganizationId(), form.getId());
        Map<UUID, Integer> counts = fieldCounts(form.getOrganizationId(), all.stream().map(FormTemplateVersion::getId).toList());
        return new FormDetailResponse(form.getId(), form.getName(), form.getDescription(), form.getStatus(), form.getArchivedAt(),
                form.getCreatedAt(), form.getUpdatedAt(), form.getVersion(),
                all.stream().map(v -> versionSummary(v, counts.getOrDefault(v.getId(), 0))).toList());
    }

    private FormVersionDetailResponse versionDetail(UUID organizationId, FormTemplateVersion version) {
        List<FormFieldResponse> mapped = fields.findAllByOrganizationIdAndFormVersionIdOrderByDisplayOrderAscIdAsc(organizationId, version.getId())
                .stream().map(FormTemplateService::fieldResponse).toList();
        return new FormVersionDetailResponse(version.getId(), version.getFormId(), version.getVersionNumber(), version.getStatus(),
                version.getChangeNote(), version.getPublishedAt(), version.getUpdatedAt(), version.getVersion(), mapped);
    }

    private Map<UUID, Integer> fieldCounts(UUID organizationId, Collection<UUID> versionIds) {
        if (versionIds.isEmpty()) return Map.of();
        Map<UUID, Integer> counts = new HashMap<>();
        for (FormField field : fields.findAllByOrganizationIdAndFormVersionIdIn(organizationId, versionIds)) {
            counts.merge(field.getFormVersionId(), 1, Integer::sum);
        }
        return counts;
    }

    private static FormSummaryResponse summary(FormTemplate form, List<FormTemplateVersion> versions) {
        FormTemplateVersion published = versions.stream().filter(v -> v.getStatus() == FormVersionStatus.PUBLISHED)
                .max(Comparator.comparingInt(FormTemplateVersion::getVersionNumber)).orElse(null);
        FormTemplateVersion draft = versions.stream().filter(v -> v.getStatus() == FormVersionStatus.DRAFT).findFirst().orElse(null);
        return new FormSummaryResponse(form.getId(), form.getName(), form.getDescription(), form.getStatus(),
                published == null ? null : published.getVersionNumber(), published == null ? null : published.getId(),
                draft == null ? null : draft.getVersionNumber(), form.getUpdatedAt(), form.getVersion());
    }

    private static FormVersionSummaryResponse versionSummary(FormTemplateVersion version, int fieldCount) {
        return new FormVersionSummaryResponse(version.getId(), version.getVersionNumber(), version.getStatus(), version.getChangeNote(),
                version.getPublishedAt(), version.getUpdatedAt(), fieldCount, version.getVersion());
    }

    public static FormFieldResponse fieldResponse(FormField field) {
        return new FormFieldResponse(field.getId(), field.getFieldKey(), field.getLabel(), field.getHelpText(), field.getFieldType(),
                field.getDisplayOrder(), field.isRequired(), field.getConditionExpression(), field.getConfigurationJson());
    }

    private FormTemplate requireForm(UUID organizationId, UUID formId) {
        return forms.findByOrganizationIdAndId(organizationId, formId).orElseThrow(FormTemplateService::notFound);
    }

    private FormTemplate requireFormForUpdate(UUID organizationId, UUID formId) {
        return forms.findForUpdate(organizationId, formId).orElseThrow(FormTemplateService::notFound);
    }

    private FormTemplate requireActiveFormForUpdate(UUID organizationId, UUID formId) {
        FormTemplate form = requireFormForUpdate(organizationId, formId);
        if (form.getStatus() != FormStatus.ACTIVE) throw new ApiException(HttpStatus.CONFLICT, "FORM_ARCHIVED", "Archived forms cannot be versioned.");
        return form;
    }

    private void requireActiveForm(UUID organizationId, UUID formId) {
        if (requireForm(organizationId, formId).getStatus() != FormStatus.ACTIVE) throw new ApiException(HttpStatus.CONFLICT, "FORM_ARCHIVED", "Archived forms cannot be changed.");
    }

    private static void requireVersion(long actual, long expected) {
        if (actual != expected) throw new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "The resource changed. Refresh and try again.");
    }

    private static int safeSize(int size) { return Math.min(Math.max(size, 1), MAX_PAGE_SIZE); }
    private static String normalizedName(String value) {
        try { return FormTemplate.text(value, "Form name", 180); } catch (IllegalArgumentException ex) { throw invalid(ex.getMessage()); }
    }
    private static String safe(String value) { return value == null ? "" : value; }
    private static ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Requested form was not found."); }
    private static ApiException immutable() { return new ApiException(HttpStatus.CONFLICT, "FORM_VERSION_IMMUTABLE", "Published form versions are immutable. Create a new version to make changes."); }
    private static ApiException duplicateName() { return new ApiException(HttpStatus.CONFLICT, "FORM_NAME_EXISTS", "An active form with this name already exists."); }
    private static ApiException invalid(String message) { return new ApiException(HttpStatus.BAD_REQUEST, "FORM_INVALID", message); }

    public record PageResult<T>(List<T> items, int page, int size, long totalElements, int totalPages) {
        public PageResult { items = List.copyOf(items); }
    }
}
