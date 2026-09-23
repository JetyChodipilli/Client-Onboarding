package com.brainserve.clientonboarding.forms.domain.repository;

import com.brainserve.clientonboarding.common.domain.model.PageSlice;
import com.brainserve.clientonboarding.forms.domain.model.FormModels.*;
import com.brainserve.clientonboarding.forms.domain.model.FormField;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface FormRepository {
    PageSlice<Template> templates(UUID organizationId, String search, int page, int size);
    Optional<Template> template(UUID organizationId, UUID id);
    void insertTemplate(Template value, UUID actor);
    boolean lockTemplate(UUID organizationId, UUID id);
    boolean archive(UUID organizationId, UUID id, long version, UUID actor, Instant now);
    PageSlice<Definition> versions(UUID organizationId, UUID formId, int page, int size);
    int nextVersion(UUID organizationId, UUID formId);
    Optional<Definition> definition(UUID organizationId, UUID id);
    void insertDefinition(Definition value, UUID actor);
    boolean updateDefinition(UUID organizationId, UUID id, long version, List<FormField> fields,
                             boolean publish, UUID actor, Instant now);
    Optional<Response> response(UUID organizationId, UUID stepId);
    void insertResponse(Response value, UUID actor);
    boolean updateResponse(Response value, long expectedVersion, UUID actor);
    void insertSubmission(UUID organizationId, Response value, UUID actor, Instant now);
    void insertReview(UUID organizationId, Response value, ReviewDecision decision, String note, UUID actor, Instant now);
    PageSlice<Submission> submissions(UUID organizationId, UUID responseId, int page, int size);
    void event(Response response, String type, String correlationId, Instant now);
}
