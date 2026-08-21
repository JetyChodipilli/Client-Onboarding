package com.brainserve.onboarding.forms.application.service;

import com.brainserve.onboarding.audit.application.service.AuditService;
import com.brainserve.onboarding.client.application.service.ClientPortalAccessService;
import com.brainserve.onboarding.client.domain.model.ClientProjectAccessLevel;
import com.brainserve.onboarding.common.activity.ActivityTimelineService;
import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.common.event.OutboxService;
import com.brainserve.onboarding.common.security.ClientPrincipal;
import com.brainserve.onboarding.common.security.TenantPrincipal;
import com.brainserve.onboarding.forms.api.request.RequestFormRevisionRequest;
import com.brainserve.onboarding.forms.api.request.SaveFormResponseDraftRequest;
import com.brainserve.onboarding.forms.api.request.SubmitFormResponseRequest;
import com.brainserve.onboarding.forms.api.response.ClientFormStepResponse;
import com.brainserve.onboarding.forms.api.response.FormSubmissionResponse;
import com.brainserve.onboarding.forms.api.response.FormReviewQueueItemResponse;
import com.brainserve.onboarding.forms.domain.model.FormAnswer;
import com.brainserve.onboarding.forms.domain.model.FormField;
import com.brainserve.onboarding.forms.domain.model.FormSubmission;
import com.brainserve.onboarding.forms.domain.model.FormSubmissionStatus;
import com.brainserve.onboarding.forms.domain.model.FormTemplate;
import com.brainserve.onboarding.forms.domain.model.FormTemplateVersion;
import com.brainserve.onboarding.forms.domain.model.FormVersionStatus;
import com.brainserve.onboarding.forms.infrastructure.persistence.FormAnswerRepository;
import com.brainserve.onboarding.forms.infrastructure.persistence.FormFieldRepository;
import com.brainserve.onboarding.forms.infrastructure.persistence.FormSubmissionRepository;
import com.brainserve.onboarding.forms.infrastructure.persistence.FormTemplateRepository;
import com.brainserve.onboarding.forms.infrastructure.persistence.FormTemplateVersionRepository;
import com.brainserve.onboarding.onboarding.application.service.OnboardingStepAccessService;
import com.brainserve.onboarding.onboarding.application.service.OnboardingStepCommandService;
import com.brainserve.onboarding.onboarding.application.service.WorkflowActorType;
import com.brainserve.onboarding.onboarding.domain.model.OnboardingStepStatus;
import com.brainserve.onboarding.project.application.service.ProjectPortalQueryService;
import com.brainserve.onboarding.workflow.domain.model.WorkflowStepType;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
public class FormSubmissionService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<OnboardingStepStatus> CLIENT_EDITABLE = Set.of(
            OnboardingStepStatus.AVAILABLE, OnboardingStepStatus.IN_PROGRESS, OnboardingStepStatus.NEEDS_REVISION);

    private final FormSubmissionRepository submissions;
    private final FormAnswerRepository answers;
    private final FormFieldRepository fields;
    private final FormTemplateVersionRepository versions;
    private final FormTemplateRepository forms;
    private final FormAnswerValidator answerValidator;
    private final FormFileReferenceValidator fileReferences;
    private final OnboardingStepAccessService stepAccess;
    private final OnboardingStepCommandService stepCommands;
    private final ClientPortalAccessService clientAccess;
    private final ProjectPortalQueryService projects;
    private final ActivityTimelineService activity;
    private final AuditService audit;
    private final OutboxService outbox;
    private final Clock clock;

    public FormSubmissionService(FormSubmissionRepository submissions, FormAnswerRepository answers,
                                 FormFieldRepository fields, FormTemplateVersionRepository versions,
                                 FormTemplateRepository forms, FormAnswerValidator answerValidator,
                                 FormFileReferenceValidator fileReferences, OnboardingStepAccessService stepAccess, OnboardingStepCommandService stepCommands,
                                 ClientPortalAccessService clientAccess, ProjectPortalQueryService projects,
                                 ActivityTimelineService activity, AuditService audit, OutboxService outbox, Clock clock) {
        this.submissions = submissions;
        this.answers = answers;
        this.fields = fields;
        this.versions = versions;
        this.forms = forms;
        this.answerValidator = answerValidator;
        this.fileReferences = fileReferences;
        this.stepAccess = stepAccess;
        this.stepCommands = stepCommands;
        this.clientAccess = clientAccess;
        this.projects = projects;
        this.activity = activity;
        this.audit = audit;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ClientFormStepResponse clientStep(ClientPrincipal principal, UUID projectId, UUID stepId) {
        ClientPortalAccessService.ProjectAccess grant = clientAccess.requireProjectAccess(principal.organizationId(), principal.userId(), projectId);
        requireQuestionnaireAccess(grant);
        FormContext context = requireFormStep(principal.organizationId(), projectId, stepId, true);
        List<FormSubmission> history = submissions.findAllByOrganizationIdAndStepInstanceIdOrderBySubmissionNumberDesc(principal.organizationId(), stepId);
        return clientResponse(context, history, principal.organizationId());
    }

    @Transactional
    public ClientFormStepResponse saveDraft(ClientPrincipal principal, UUID projectId, UUID stepId,
                                            SaveFormResponseDraftRequest request, HttpServletRequest servletRequest) {
        ClientPortalAccessService.ProjectAccess grant = clientAccess.requireProjectAccess(principal.organizationId(), principal.userId(), projectId);
        requireQuestionnaireAccess(grant);
        FormContext context = requireFormStep(principal.organizationId(), projectId, stepId, true);
        requireClientEditable(context.step());

        FormSubmission draft = submissions.findForUpdateByStepAndStatus(principal.organizationId(), stepId, FormSubmissionStatus.DRAFT).orElse(null);
        Instant now = clock.instant();
        boolean created = false;
        if (draft == null) {
            if (request.version() != 0L) throw versionConflict();
            int number = submissions.findFirstByOrganizationIdAndStepInstanceIdOrderBySubmissionNumberDesc(principal.organizationId(), stepId)
                    .map(value -> value.getSubmissionNumber() + 1).orElse(1);
            FormSubmission latest = submissions.findFirstByOrganizationIdAndStepInstanceIdOrderBySubmissionNumberDesc(principal.organizationId(), stepId).orElse(null);
            draft = submissions.saveAndFlush(new FormSubmission(UUID.randomUUID(), principal.organizationId(), context.step().onboardingId(), stepId,
                    projectId, grant.clientUserId(), context.version().getId(), number, latest == null ? null : latest.getId(), principal.userId(), now));
            created = true;
        } else {
            requireVersion(draft.getVersion(), request.version());
        }

        Map<String, JsonNode> normalized = normalizeAnswers(request.answers());
        answerValidator.validateDraft(new FormAnswerValidator.ListView(context.fields()), normalized);
        fileReferences.validate(principal.organizationId(), projectId, stepId, draft.getId(), context.fields(), normalized);
        persistDraftAnswers(principal.organizationId(), draft, context.fields(), normalized, now);
        draft.touch(principal.userId(), now);
        submissions.saveAndFlush(draft);
        transitionToInProgressIfNeeded(principal.organizationId(), context.step(), principal.userId());

        activity.recordClient(principal.organizationId(), grant.clientId(), projectId, principal.userId(), "FORM_DRAFT_SAVED",
                "FORM_SUBMISSION", draft.getId(), "Questionnaire draft saved", Map.of("stepId", stepId, "submissionNumber", draft.getSubmissionNumber()));
        audit.recordClient(principal.organizationId(), principal.userId(), "FORM_DRAFT_SAVED", "FORM_SUBMISSION", draft.getId(),
                null, Map.of("stepId", stepId, "submissionNumber", draft.getSubmissionNumber(), "created", created), servletRequest);
        List<FormSubmission> history = submissions.findAllByOrganizationIdAndStepInstanceIdOrderBySubmissionNumberDesc(principal.organizationId(), stepId);
        return clientResponse(requireFormStep(principal.organizationId(), projectId, stepId, true), history, principal.organizationId());
    }

    @Transactional
    public ClientFormStepResponse submit(ClientPrincipal principal, UUID projectId, UUID stepId,
                                         SubmitFormResponseRequest request, HttpServletRequest servletRequest) {
        ClientPortalAccessService.ProjectAccess grant = clientAccess.requireProjectAccess(principal.organizationId(), principal.userId(), projectId);
        requireQuestionnaireAccess(grant);
        FormContext context = requireFormStep(principal.organizationId(), projectId, stepId, true);
        requireClientEditable(context.step());
        FormSubmission draft = submissions.findForUpdateByStepAndStatus(principal.organizationId(), stepId, FormSubmissionStatus.DRAFT)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "FORM_DRAFT_REQUIRED", "Save the questionnaire draft before submitting."));
        requireVersion(draft.getVersion(), request.version());
        Map<String, JsonNode> stored = answersByKey(principal.organizationId(), draft.getId(), context.fields());
        answerValidator.validateSubmission(new FormAnswerValidator.ListView(context.fields()), stored);
        fileReferences.validate(principal.organizationId(), projectId, stepId, draft.getId(), context.fields(), stored);
        transitionToInProgressIfNeeded(principal.organizationId(), context.step(), principal.userId());
        Instant now = clock.instant();
        draft.submit(principal.userId(), now);
        submissions.saveAndFlush(draft);
        stepCommands.transition(principal.organizationId(), context.step().onboardingId(), stepId, OnboardingStepStatus.SUBMITTED,
                principal.userId(), WorkflowActorType.CLIENT);
        if (!context.step().requiresReview()) {
            draft.autoApprove(principal.userId(), now);
            submissions.saveAndFlush(draft);
            stepCommands.transition(principal.organizationId(), context.step().onboardingId(), stepId, OnboardingStepStatus.COMPLETED,
                    principal.userId(), WorkflowActorType.CLIENT);
            outbox.record(principal.organizationId(), "FORM_APPROVED", "FORM_SUBMISSION", draft.getId(),
                    Map.of("submissionId", draft.getId(), "stepId", stepId, "projectId", projectId, "automatic", true));
        }
        outbox.record(principal.organizationId(), "FORM_SUBMITTED", "FORM_SUBMISSION", draft.getId(),
                Map.of("submissionId", draft.getId(), "stepId", stepId, "projectId", projectId, "submissionNumber", draft.getSubmissionNumber()));
        activity.recordClient(principal.organizationId(), grant.clientId(), projectId, principal.userId(), "FORM_SUBMITTED",
                "FORM_SUBMISSION", draft.getId(), context.step().name() + " submitted", Map.of("stepId", stepId, "submissionNumber", draft.getSubmissionNumber()));
        audit.recordClient(principal.organizationId(), principal.userId(), "FORM_SUBMITTED", "FORM_SUBMISSION", draft.getId(), null,
                Map.of("stepId", stepId, "submissionNumber", draft.getSubmissionNumber(), "status", draft.getStatus()), servletRequest);
        List<FormSubmission> history = submissions.findAllByOrganizationIdAndStepInstanceIdOrderBySubmissionNumberDesc(principal.organizationId(), stepId);
        return clientResponse(requireFormStep(principal.organizationId(), projectId, stepId, true), history, principal.organizationId());
    }

    @Transactional(readOnly = true)
    public FormFileUploadContext fileUploadContext(ClientPrincipal principal, UUID projectId, UUID stepId, UUID fieldId) {
        ClientPortalAccessService.ProjectAccess grant = clientAccess.requireProjectAccess(principal.organizationId(), principal.userId(), projectId);
        requireQuestionnaireAccess(grant);
        FormContext context = requireFormStep(principal.organizationId(), projectId, stepId, true);
        requireClientEditable(context.step());
        FormSubmission draft = submissions.findByOrganizationIdAndStepInstanceIdAndStatus(principal.organizationId(), stepId, FormSubmissionStatus.DRAFT)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "FORM_DRAFT_REQUIRED",
                        "Save the questionnaire draft before uploading a file answer."));
        FormField field = context.fields().stream().filter(value -> value.getId().equals(fieldId)).findFirst().orElseThrow(FormSubmissionService::notFound);
        if (field.getFieldType() != com.brainserve.onboarding.forms.domain.model.FormFieldType.FILE) throw notFound();
        return new FormFileUploadContext(principal.organizationId(), grant.clientId(), grant.clientUserId(), projectId, context.step().onboardingId(),
                stepId, context.step().name(), context.version().getId(), draft.getId(), draft.getVersion(), field.getId(), field.getFieldKey(), field.getLabel(),
                field.getHelpText(), field.isRequired(), field.getConfigurationJson());
    }

    @Transactional(readOnly = true)
    public FormFileReadContext fileReadContext(ClientPrincipal principal, UUID projectId, UUID stepId, UUID fieldId) {
        ClientPortalAccessService.ProjectAccess grant = clientAccess.requireProjectAccess(principal.organizationId(), principal.userId(), projectId);
        requireQuestionnaireAccess(grant);
        FormContext context = requireFormStep(principal.organizationId(), projectId, stepId, true);
        FormField field = context.fields().stream().filter(value -> value.getId().equals(fieldId)).findFirst().orElseThrow(FormSubmissionService::notFound);
        if (field.getFieldType() != com.brainserve.onboarding.forms.domain.model.FormFieldType.FILE) throw notFound();
        return new FormFileReadContext(principal.organizationId(), grant.clientId(), grant.clientUserId(), projectId, context.step().onboardingId(),
                stepId, context.version().getId(), field.getId(), field.getFieldKey(), field.getLabel(), field.getHelpText(), field.isRequired(), field.getConfigurationJson());
    }

    @Transactional(readOnly = true)
    public FormSubmissionResponse getSubmission(TenantPrincipal principal, UUID submissionId) {
        FormSubmission submission = requireSubmission(principal.organizationId(), submissionId);
        FormContext context = requireFormStep(principal.organizationId(), submission.getProjectId(), submission.getStepInstanceId(), false);
        return response(submission, context.fields(), principal.organizationId());
    }

    @Transactional(readOnly = true)
    public List<FormSubmissionResponse> history(TenantPrincipal principal, UUID stepId) {
        OnboardingStepAccessService.StepRef step = stepAccess.require(principal.organizationId(), stepId);
        requireFormType(step);
        FormContext context = requireFormStep(principal.organizationId(), step.projectId(), stepId, false);
        return responses(submissions.findAllByOrganizationIdAndStepInstanceIdOrderBySubmissionNumberDesc(principal.organizationId(), stepId),
                context.fields(), principal.organizationId());
    }

    @Transactional(readOnly = true)
    public PageResult<FormReviewQueueItemResponse> reviewQueue(TenantPrincipal principal, FormSubmissionStatus status, int page, int size) {
        if (status == FormSubmissionStatus.DRAFT) throw new ApiException(HttpStatus.BAD_REQUEST, "FORM_REVIEW_STATUS_INVALID", "Draft responses are not part of the internal review queue.");
        var pageable = PageRequest.of(Math.max(page, 0), safeSize(size), Sort.by(Sort.Direction.DESC, "updatedAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<FormSubmission> result = status == null
                ? submissions.findAllByOrganizationId(principal.organizationId(), pageable)
                : submissions.findAllByOrganizationIdAndStatus(principal.organizationId(), status, pageable);
        List<FormReviewQueueItemResponse> items = result.getContent().stream().map(submission -> reviewItem(principal.organizationId(), submission)).toList();
        return new PageResult<>(items, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public FormSubmissionResponse startReview(TenantPrincipal principal, UUID submissionId, long expectedVersion, HttpServletRequest servletRequest) {
        FormSubmission submission = requireForUpdate(principal.organizationId(), submissionId);
        requireVersion(submission.getVersion(), expectedVersion);
        FormContext context = requireFormStep(principal.organizationId(), submission.getProjectId(), submission.getStepInstanceId(), false);
        try { submission.startReview(principal.userId(), clock.instant()); }
        catch (IllegalStateException ex) { throw state(ex.getMessage()); }
        submissions.saveAndFlush(submission);
        stepCommands.transition(principal.organizationId(), submission.getOnboardingId(), submission.getStepInstanceId(), OnboardingStepStatus.UNDER_REVIEW, principal.userId());
        recordInternal(principal, submission, context, "FORM_REVIEW_STARTED", "Questionnaire review started", servletRequest, Map.of());
        return response(submission, context.fields(), principal.organizationId());
    }

    @Transactional
    public FormSubmissionResponse approve(TenantPrincipal principal, UUID submissionId, long expectedVersion, HttpServletRequest servletRequest) {
        FormSubmission submission = requireForUpdate(principal.organizationId(), submissionId);
        requireVersion(submission.getVersion(), expectedVersion);
        FormContext context = requireFormStep(principal.organizationId(), submission.getProjectId(), submission.getStepInstanceId(), false);
        if (submission.getStatus() == FormSubmissionStatus.SUBMITTED) {
            try { submission.startReview(principal.userId(), clock.instant()); }
            catch (IllegalStateException ex) { throw state(ex.getMessage()); }
            submissions.saveAndFlush(submission);
            stepCommands.transition(principal.organizationId(), submission.getOnboardingId(), submission.getStepInstanceId(), OnboardingStepStatus.UNDER_REVIEW, principal.userId());
        }
        try { submission.approve(principal.userId(), clock.instant()); }
        catch (IllegalStateException ex) { throw state(ex.getMessage()); }
        submissions.saveAndFlush(submission);
        stepCommands.transition(principal.organizationId(), submission.getOnboardingId(), submission.getStepInstanceId(), OnboardingStepStatus.COMPLETED, principal.userId());
        outbox.record(principal.organizationId(), "FORM_APPROVED", "FORM_SUBMISSION", submission.getId(),
                Map.of("submissionId", submission.getId(), "stepId", submission.getStepInstanceId(), "projectId", submission.getProjectId(), "automatic", false));
        recordInternal(principal, submission, context, "FORM_APPROVED", "Questionnaire approved", servletRequest, Map.of());
        return response(submission, context.fields(), principal.organizationId());
    }

    @Transactional
    public FormSubmissionResponse requestRevision(TenantPrincipal principal, UUID submissionId, RequestFormRevisionRequest request,
                                                  HttpServletRequest servletRequest) {
        FormSubmission submission = requireForUpdate(principal.organizationId(), submissionId);
        requireVersion(submission.getVersion(), request.version());
        FormContext context = requireFormStep(principal.organizationId(), submission.getProjectId(), submission.getStepInstanceId(), false);
        if (submission.getStatus() == FormSubmissionStatus.SUBMITTED) {
            try { submission.startReview(principal.userId(), clock.instant()); }
            catch (IllegalStateException ex) { throw state(ex.getMessage()); }
            submissions.saveAndFlush(submission);
            stepCommands.transition(principal.organizationId(), submission.getOnboardingId(), submission.getStepInstanceId(), OnboardingStepStatus.UNDER_REVIEW, principal.userId());
        }
        Instant now = clock.instant();
        try { submission.requestRevision(principal.userId(), request.reason(), now); }
        catch (IllegalArgumentException | IllegalStateException ex) { throw state(ex.getMessage()); }
        submissions.saveAndFlush(submission);
        stepCommands.transition(principal.organizationId(), submission.getOnboardingId(), submission.getStepInstanceId(), OnboardingStepStatus.NEEDS_REVISION, principal.userId());

        // Preserve the reviewed attempt and create a new mutable version with copied answers.
        FormSubmission replacement = submissions.saveAndFlush(new FormSubmission(UUID.randomUUID(), principal.organizationId(), submission.getOnboardingId(),
                submission.getStepInstanceId(), submission.getProjectId(), submission.getClientUserId(), submission.getFormVersionId(),
                submission.getSubmissionNumber() + 1, submission.getId(), principal.userId(), now));
        List<FormAnswer> oldAnswers = answers.findAllByOrganizationIdAndSubmissionId(principal.organizationId(), submission.getId());
        answers.saveAllAndFlush(oldAnswers.stream().map(answer -> new FormAnswer(UUID.randomUUID(), principal.organizationId(), replacement.getId(),
                answer.getFieldId(), answer.getValueJson(), now)).toList());
        outbox.record(principal.organizationId(), "FORM_REVISION_REQUESTED", "FORM_SUBMISSION", submission.getId(),
                Map.of("submissionId", submission.getId(), "replacementSubmissionId", replacement.getId(), "stepId", submission.getStepInstanceId(),
                        "projectId", submission.getProjectId()));
        recordInternal(principal, submission, context, "FORM_REVISION_REQUESTED", "Questionnaire revision requested", servletRequest,
                Map.of("replacementSubmissionId", replacement.getId(), "reason", request.reason()));
        return response(submission, context.fields(), principal.organizationId());
    }

    private void persistDraftAnswers(UUID organizationId, FormSubmission submission, List<FormField> formFields,
                                     Map<String, JsonNode> supplied, Instant now) {
        Map<String, FormField> byKey = formFields.stream().collect(Collectors.toMap(FormField::getFieldKey, Function.identity()));
        Map<UUID, FormAnswer> existing = answers.findAllByOrganizationIdAndSubmissionId(organizationId, submission.getId()).stream()
                .collect(Collectors.toMap(FormAnswer::getFieldId, Function.identity()));
        Set<UUID> suppliedIds = supplied.keySet().stream().map(key -> byKey.get(key).getId()).collect(Collectors.toSet());
        for (FormAnswer answer : existing.values()) {
            if (!suppliedIds.contains(answer.getFieldId())) answers.delete(answer);
        }
        for (Map.Entry<String, JsonNode> entry : supplied.entrySet()) {
            FormField field = byKey.get(entry.getKey());
            JsonNode value = entry.getValue();
            if (!FormConditionEngine.hasValue(value)) continue;
            FormAnswer answer = existing.get(field.getId());
            if (answer == null) answers.save(new FormAnswer(UUID.randomUUID(), organizationId, submission.getId(), field.getId(), value, now));
            else { answer.update(value, now); answers.save(answer); }
        }
        answers.flush();
    }

    private void transitionToInProgressIfNeeded(UUID organizationId, OnboardingStepAccessService.StepRef step, UUID actorId) {
        if (step.status() == OnboardingStepStatus.AVAILABLE || step.status() == OnboardingStepStatus.NEEDS_REVISION) {
            stepCommands.transition(organizationId, step.onboardingId(), step.id(), OnboardingStepStatus.IN_PROGRESS, actorId, WorkflowActorType.CLIENT);
        }
    }

    private void recordInternal(TenantPrincipal principal, FormSubmission submission, FormContext context, String action,
                                String summary, HttpServletRequest request, Map<String, ?> extra) {
        var project = projects.require(principal.organizationId(), submission.getProjectId());
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("stepId", submission.getStepInstanceId());
        metadata.put("submissionNumber", submission.getSubmissionNumber());
        metadata.putAll(extra);
        activity.record(principal.organizationId(), project.clientId(), project.id(), principal.userId(), action, "FORM_SUBMISSION",
                submission.getId(), summary, metadata);
        audit.record(principal.organizationId(), principal.userId(), action, "FORM_SUBMISSION", submission.getId(), null,
                Map.of("stepId", submission.getStepInstanceId(), "submissionNumber", submission.getSubmissionNumber(), "status", submission.getStatus()), request);
    }

    private FormReviewQueueItemResponse reviewItem(UUID organizationId, FormSubmission submission) {
        FormContext context = requireFormStep(organizationId, submission.getProjectId(), submission.getStepInstanceId(), false);
        return new FormReviewQueueItemResponse(submission.getId(), submission.getProjectId(), submission.getOnboardingId(), submission.getStepInstanceId(),
                context.step().name(), context.form().getId(), context.form().getName(), submission.getSubmissionNumber(), submission.getStatus(),
                submission.getSubmittedAt(), submission.getUpdatedAt(), submission.getVersion());
    }

    private ClientFormStepResponse clientResponse(FormContext context, List<FormSubmission> history, UUID organizationId) {
        List<FormSubmissionResponse> mapped = responses(history, context.fields(), organizationId);
        FormSubmissionResponse current = mapped.isEmpty() ? null : mapped.get(0);
        return new ClientFormStepResponse(context.step().id(), context.projectId(), context.step().name(), context.step().description(), context.step().status(),
                context.step().requiresReview(), context.form().getId(), context.form().getName(), context.version().getId(), context.version().getVersionNumber(),
                context.fields().stream().map(FormTemplateService::fieldResponse).toList(), current, mapped);
    }

    private List<FormSubmissionResponse> responses(List<FormSubmission> values, List<FormField> formFields, UUID organizationId) {
        if (values.isEmpty()) return List.of();
        Set<UUID> ids = values.stream().map(FormSubmission::getId).collect(Collectors.toSet());
        Map<UUID, List<FormAnswer>> grouped = answers.findAllByOrganizationIdAndSubmissionIdIn(organizationId, ids).stream()
                .collect(Collectors.groupingBy(FormAnswer::getSubmissionId));
        Map<UUID, String> fieldKeys = formFields.stream().collect(Collectors.toMap(FormField::getId, FormField::getFieldKey));
        return values.stream().map(value -> response(value, grouped.getOrDefault(value.getId(), List.of()), fieldKeys)).toList();
    }

    private FormSubmissionResponse response(FormSubmission submission, List<FormField> formFields, UUID organizationId) {
        Map<UUID, String> fieldKeys = formFields.stream().collect(Collectors.toMap(FormField::getId, FormField::getFieldKey));
        return response(submission, answers.findAllByOrganizationIdAndSubmissionId(organizationId, submission.getId()), fieldKeys);
    }

    private static FormSubmissionResponse response(FormSubmission submission, List<FormAnswer> values, Map<UUID, String> fieldKeys) {
        Map<String, JsonNode> mapped = new LinkedHashMap<>();
        values.stream().sorted(java.util.Comparator.comparing(a -> fieldKeys.getOrDefault(a.getFieldId(), ""))).forEach(answer -> {
            String key = fieldKeys.get(answer.getFieldId());
            if (key != null) mapped.put(key, answer.getValueJson());
        });
        return new FormSubmissionResponse(submission.getId(), submission.getOnboardingId(), submission.getStepInstanceId(), submission.getProjectId(),
                submission.getFormVersionId(), submission.getSubmissionNumber(), submission.getPreviousSubmissionId(), submission.getStatus(),
                submission.getSubmittedAt(), submission.getReviewStartedAt(), submission.getReviewedAt(), submission.getRevisionNote(),
                submission.getUpdatedAt(), submission.getVersion(), Map.copyOf(mapped));
    }

    private Map<String, JsonNode> answersByKey(UUID organizationId, UUID submissionId, List<FormField> formFields) {
        Map<UUID, String> keyById = formFields.stream().collect(Collectors.toMap(FormField::getId, FormField::getFieldKey));
        Map<String, JsonNode> result = new HashMap<>();
        for (FormAnswer answer : answers.findAllByOrganizationIdAndSubmissionId(organizationId, submissionId)) {
            String key = keyById.get(answer.getFieldId());
            if (key != null) result.put(key, answer.getValueJson());
        }
        return result;
    }

    private FormContext requireFormStep(UUID organizationId, UUID projectId, UUID stepId, boolean requireClientVisible) {
        OnboardingStepAccessService.StepRef step = stepAccess.requireForProject(organizationId, projectId, stepId);
        requireFormType(step);
        if (requireClientVisible && !step.clientVisible()) throw notFound();
        UUID formVersionId = configuredVersionId(step.configuration());
        FormTemplateVersion version = versions.findByOrganizationIdAndId(organizationId, formVersionId).orElseThrow(FormSubmissionService::notFound);
        if (version.getStatus() != FormVersionStatus.PUBLISHED) throw new ApiException(HttpStatus.CONFLICT, "FORM_VERSION_UNAVAILABLE", "The configured questionnaire version is not published.");
        FormTemplate form = forms.findByOrganizationIdAndId(organizationId, version.getFormId()).orElseThrow(FormSubmissionService::notFound);
        List<FormField> formFields = fields.findAllByOrganizationIdAndFormVersionIdOrderByDisplayOrderAscIdAsc(organizationId, version.getId());
        return new FormContext(projectId, step, form, version, formFields);
    }

    private static UUID configuredVersionId(JsonNode configuration) {
        try {
            JsonNode value = configuration == null ? null : configuration.get("formVersionId");
            if (value == null || !value.isTextual()) throw new IllegalArgumentException();
            return UUID.fromString(value.asText());
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "FORM_STEP_CONFIGURATION_INVALID", "This form workflow step has invalid configuration.");
        }
    }

    private static void requireFormType(OnboardingStepAccessService.StepRef step) {
        if (step.stepType() != WorkflowStepType.FORM) throw notFound();
    }

    private static void requireClientEditable(OnboardingStepAccessService.StepRef step) {
        if (!CLIENT_EDITABLE.contains(step.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "ONBOARDING_STEP_NOT_AVAILABLE", "This questionnaire is not currently available for editing.");
        }
    }


    private static Map<String, JsonNode> normalizeAnswers(Map<String, JsonNode> input) {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        input.forEach((key, value) -> {
            String normalized;
            try { normalized = FormField.normalizeKey(key); }
            catch (IllegalArgumentException ex) { throw new ApiException(HttpStatus.BAD_REQUEST, "FORM_ANSWER_INVALID", ex.getMessage()); }
            if (result.put(normalized, value) != null) throw new ApiException(HttpStatus.BAD_REQUEST, "FORM_ANSWER_INVALID", "Duplicate form answer key: " + normalized);
        });
        return result;
    }


    private static void requireQuestionnaireAccess(ClientPortalAccessService.ProjectAccess grant) {
        if (grant.accessLevel() != ClientProjectAccessLevel.CLIENT_ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CLIENT_TASK_NOT_ASSIGNED",
                    "This questionnaire is not assigned to your client-member account.");
        }
    }


    private FormSubmission requireSubmission(UUID organizationId, UUID id) {
        return submissions.findByOrganizationIdAndId(organizationId, id).orElseThrow(FormSubmissionService::notFound);
    }

    private FormSubmission requireForUpdate(UUID organizationId, UUID id) {
        return submissions.findForUpdate(organizationId, id).orElseThrow(FormSubmissionService::notFound);
    }

    private static void requireVersion(long actual, long expected) { if (actual != expected) throw versionConflict(); }
    private static int safeSize(int size) { return Math.min(Math.max(size, 1), MAX_PAGE_SIZE); }
    private static ApiException versionConflict() { return new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "The response changed. Refresh and try again."); }
    private static ApiException state(String message) { return new ApiException(HttpStatus.CONFLICT, "FORM_SUBMISSION_STATE_INVALID", message); }
    private static ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Requested questionnaire was not found."); }

    public record FormFileUploadContext(UUID organizationId, UUID clientId, UUID clientUserId, UUID projectId, UUID onboardingId,
                                        UUID stepId, String stepName, UUID formVersionId, UUID submissionId, long submissionVersion, UUID fieldId,
                                        String fieldKey, String label, String helpText, boolean required, JsonNode configuration) {}

    public record FormFileReadContext(UUID organizationId, UUID clientId, UUID clientUserId, UUID projectId, UUID onboardingId,
                                      UUID stepId, UUID formVersionId, UUID fieldId, String fieldKey, String label, String helpText,
                                      boolean required, JsonNode configuration) {}

    private record FormContext(UUID projectId, OnboardingStepAccessService.StepRef step, FormTemplate form,
                               FormTemplateVersion version, List<FormField> fields) {}

    public record PageResult<T>(List<T> items, int page, int size, long totalElements, int totalPages) {
        public PageResult { items = List.copyOf(items); }
    }
}
