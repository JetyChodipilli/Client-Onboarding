package com.brainserve.clientonboarding.forms.api;

import com.brainserve.clientonboarding.common.api.ApiSuccess;
import com.brainserve.clientonboarding.common.domain.model.PageSlice;
import com.brainserve.clientonboarding.common.observability.RequestIds;
import com.brainserve.clientonboarding.common.observability.RequestMetadata;
import com.brainserve.clientonboarding.common.security.CurrentPrincipal;
import com.brainserve.clientonboarding.forms.application.FormTemplateService;
import com.brainserve.clientonboarding.forms.application.FormResponseService;
import com.brainserve.clientonboarding.forms.domain.model.FormField;
import com.brainserve.clientonboarding.forms.domain.model.FormModels.Template;
import com.brainserve.clientonboarding.forms.domain.model.FormModels.Definition;
import com.brainserve.clientonboarding.forms.domain.model.FormModels.Submission;
import com.brainserve.clientonboarding.forms.domain.model.FormModels.ReviewDecision;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class FormController {
    private final FormTemplateService templates;
    private final FormResponseService responses;
    public FormController(FormTemplateService templates,FormResponseService responses) { this.templates=templates;this.responses=responses; }
    @GetMapping("/forms")
    ApiSuccess<List<Template>> list(@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size,Authentication auth) { return page(templates.list(CurrentPrincipal.require(auth),search,page,size)); }
    @PostMapping("/forms") @ResponseStatus(HttpStatus.CREATED)
    ApiSuccess<FormTemplateService.Bundle> create(@Valid @RequestBody TemplateRequest request,Authentication auth,HttpServletRequest servlet) { return ok(templates.create(CurrentPrincipal.require(auth),request.name(),request.description(),RequestMetadata.from(servlet))); }
    @GetMapping("/forms/{id}")
    ApiSuccess<Template> template(@PathVariable UUID id,Authentication auth) { return ok(templates.get(CurrentPrincipal.require(auth),id)); }
    @GetMapping("/forms/{id}/versions")
    ApiSuccess<List<Definition>> versions(@PathVariable UUID id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size,Authentication auth) { return page(templates.versions(CurrentPrincipal.require(auth),id,page,size)); }
    @PostMapping("/forms/{id}/versions") @ResponseStatus(HttpStatus.CREATED)
    ApiSuccess<Definition> newVersion(@PathVariable UUID id,@RequestBody SourceRequest request,Authentication auth,HttpServletRequest servlet) { return ok(templates.newVersion(CurrentPrincipal.require(auth),id,request.sourceVersionId(),RequestMetadata.from(servlet))); }
    @PostMapping("/forms/{id}/archive")
    ApiSuccess<Map<String,String>> archive(@PathVariable UUID id,@RequestParam @PositiveOrZero long version,Authentication auth,HttpServletRequest servlet) { templates.archive(CurrentPrincipal.require(auth),id,version,RequestMetadata.from(servlet)); return ok(Map.of("message","Form archived. Existing responses remain available.")); }
    @GetMapping("/form-versions/{id}")
    ApiSuccess<Definition> definition(@PathVariable UUID id,Authentication auth) { return ok(templates.definition(CurrentPrincipal.require(auth),id)); }
    @PutMapping("/form-versions/{id}/fields")
    ApiSuccess<Definition> fields(@PathVariable UUID id,@Valid @RequestBody FieldsRequest request,Authentication auth,HttpServletRequest servlet) { return ok(templates.update(CurrentPrincipal.require(auth),id,request.version(),request.fields(),false,RequestMetadata.from(servlet))); }
    @PostMapping("/form-versions/{id}/publish")
    ApiSuccess<Definition> publish(@PathVariable UUID id,@RequestParam @PositiveOrZero long version,Authentication auth,HttpServletRequest servlet) { return ok(templates.update(CurrentPrincipal.require(auth),id,version,null,true,RequestMetadata.from(servlet))); }
    @GetMapping("/form-responses/{stepId}")
    ApiSuccess<FormResponseService.View> internal(@PathVariable UUID stepId,Authentication auth) { return ok(responses.internal(CurrentPrincipal.require(auth),stepId)); }
    @GetMapping("/form-responses/{stepId}/submissions")
    ApiSuccess<List<Submission>> history(@PathVariable UUID stepId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size,Authentication auth) { return page(responses.history(CurrentPrincipal.require(auth),null,stepId,page,size)); }
    @PostMapping("/form-responses/{stepId}/review")
    ApiSuccess<FormResponseService.View> review(@PathVariable UUID stepId,@Valid @RequestBody ReviewRequest request,Authentication auth,HttpServletRequest servlet) { return ok(responses.review(CurrentPrincipal.require(auth),stepId,request.version(),request.decision(),request.note(),RequestMetadata.from(servlet))); }
    @GetMapping("/client-portal/projects/{projectId}/forms/{stepId}")
    ApiSuccess<FormResponseService.View> client(@PathVariable UUID projectId,@PathVariable UUID stepId,Authentication auth) { return ok(responses.client(CurrentPrincipal.require(auth),projectId,stepId)); }
    @GetMapping("/client-portal/projects/{projectId}/forms/{stepId}/submissions")
    ApiSuccess<List<Submission>> clientHistory(@PathVariable UUID projectId,@PathVariable UUID stepId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size,Authentication auth) { return page(responses.history(CurrentPrincipal.require(auth),projectId,stepId,page,size)); }
    @PutMapping("/client-portal/projects/{projectId}/forms/{stepId}/draft")
    ApiSuccess<FormResponseService.View> save(@PathVariable UUID projectId,@PathVariable UUID stepId,@Valid @RequestBody AnswersRequest request,Authentication auth,HttpServletRequest servlet) { return ok(responses.save(CurrentPrincipal.require(auth),projectId,stepId,request.version(),request.answers(),false,RequestMetadata.from(servlet))); }
    @PostMapping("/client-portal/projects/{projectId}/forms/{stepId}/submit")
    ApiSuccess<FormResponseService.View> submit(@PathVariable UUID projectId,@PathVariable UUID stepId,@Valid @RequestBody AnswersRequest request,Authentication auth,HttpServletRequest servlet) { return ok(responses.save(CurrentPrincipal.require(auth),projectId,stepId,request.version(),request.answers(),true,RequestMetadata.from(servlet))); }
    private <T> ApiSuccess<T> ok(T value) { return ApiSuccess.of(value,RequestIds.currentRequestId()); }
    private <T> ApiSuccess<List<T>> page(PageSlice<T> value) { return new ApiSuccess<>(true,value.items(),value.meta(),RequestIds.currentRequestId()); }
    public record TemplateRequest(@NotBlank @Size(max=180) String name,@Size(max=1000) String description) { }
    public record SourceRequest(UUID sourceVersionId) { }
    public record FieldsRequest(@PositiveOrZero long version,@NotNull @Size(min=1,max=100) List<@NotNull FormField> fields) { }
    public record AnswersRequest(@PositiveOrZero long version,@NotNull @Size(max=100) Map<String,Object> answers) { }
    public record ReviewRequest(@PositiveOrZero long version,@NotNull ReviewDecision decision,@Size(max=2000) String note) { }
}
