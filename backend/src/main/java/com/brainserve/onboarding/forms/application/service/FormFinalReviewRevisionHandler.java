package com.brainserve.onboarding.forms.application.service;

import com.brainserve.onboarding.common.event.OutboxService;
import com.brainserve.onboarding.forms.domain.model.FormAnswer;
import com.brainserve.onboarding.forms.domain.model.FormSubmission;
import com.brainserve.onboarding.forms.domain.model.FormSubmissionStatus;
import com.brainserve.onboarding.forms.infrastructure.persistence.FormAnswerRepository;
import com.brainserve.onboarding.forms.infrastructure.persistence.FormSubmissionRepository;
import com.brainserve.onboarding.onboarding.application.service.FinalReviewRevisionHandler;
import com.brainserve.onboarding.workflow.domain.model.WorkflowStepType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class FormFinalReviewRevisionHandler implements FinalReviewRevisionHandler {
    private final FormSubmissionRepository submissions;
    private final FormAnswerRepository answers;
    private final OutboxService outbox;

    public FormFinalReviewRevisionHandler(FormSubmissionRepository submissions, FormAnswerRepository answers, OutboxService outbox) {
        this.submissions = submissions;
        this.answers = answers;
        this.outbox = outbox;
    }

    @Override public boolean supports(WorkflowStepType stepType) { return stepType == WorkflowStepType.FORM; }

    @Override
    public void reopen(RevisionContext context) {
        FormSubmission approved = submissions.findFirstByOrganizationIdAndStepInstanceIdOrderBySubmissionNumberDesc(
                        context.organizationId(), context.stepId())
                .filter(value -> value.getStatus() == FormSubmissionStatus.APPROVED)
                .orElseThrow(() -> new IllegalStateException("The questionnaire does not have an approved submission to reopen."));
        approved.requestFinalReviewRevision(context.reviewerUserId(), context.reason(), context.occurredAt());
        submissions.saveAndFlush(approved);

        FormSubmission replacement = submissions.saveAndFlush(new FormSubmission(
                UUID.randomUUID(), context.organizationId(), context.onboardingId(), context.stepId(), context.projectId(),
                approved.getClientUserId(), approved.getFormVersionId(), approved.getSubmissionNumber() + 1,
                approved.getId(), context.reviewerUserId(), context.occurredAt()));
        List<FormAnswer> previous = answers.findAllByOrganizationIdAndSubmissionId(context.organizationId(), approved.getId());
        answers.saveAllAndFlush(previous.stream().map(answer -> new FormAnswer(
                UUID.randomUUID(), context.organizationId(), replacement.getId(), answer.getFieldId(),
                answer.getValueJson(), context.occurredAt())).toList());
        outbox.record(context.organizationId(), "FORM_REVISION_REQUESTED", "FORM_SUBMISSION", approved.getId(),
                Map.of("submissionId", approved.getId(), "replacementSubmissionId", replacement.getId(),
                        "stepId", context.stepId(), "projectId", context.projectId(), "source", "FINAL_REVIEW"));
    }
}
