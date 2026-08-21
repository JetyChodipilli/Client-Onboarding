package com.brainserve.onboarding.forms.api.response;

import com.brainserve.onboarding.forms.domain.model.FormSubmissionStatus;
import java.time.Instant;
import java.util.UUID;

public record FormReviewQueueItemResponse(
        UUID submissionId,
        UUID projectId,
        UUID onboardingId,
        UUID stepId,
        String stepName,
        UUID formId,
        String formName,
        int submissionNumber,
        FormSubmissionStatus status,
        Instant submittedAt,
        Instant updatedAt,
        long version) {}
