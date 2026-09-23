package com.brainserve.clientonboarding.forms.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class FormModels {
    private FormModels() { }
    public enum VersionStatus { DRAFT, PUBLISHED }
    public enum ResponseStatus { DRAFT, SUBMITTED, UNDER_REVIEW, NEEDS_REVISION, APPROVED }
    public enum ReviewDecision { UNDER_REVIEW, NEEDS_REVISION, APPROVED }
    public record Template(UUID id, UUID organizationId, String name, String description, Instant archivedAt,
                           Instant createdAt, Instant updatedAt, long version) { }
    public record Definition(UUID id, UUID organizationId, UUID formId, int versionNumber,
                             VersionStatus status, List<FormField> fields, Instant publishedAt,
                             Instant createdAt, Instant updatedAt, long version) { }
    public record Response(UUID id, UUID organizationId, UUID stepId, UUID formVersionId, ResponseStatus status,
                           Map<String,Object> answers, int submissionNumber, String reviewNote,
                           Instant updatedAt, long version) { }
    public record Submission(UUID id, int submissionNumber, Map<String,Object> answers,
                             Instant createdAt, UUID createdBy, List<Review> reviews) { }
    public record Review(ReviewDecision decision, String note, Instant createdAt, UUID createdBy) { }
}
