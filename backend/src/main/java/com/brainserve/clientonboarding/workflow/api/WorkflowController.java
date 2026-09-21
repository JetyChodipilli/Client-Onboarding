package com.brainserve.clientonboarding.workflow.api;

import com.brainserve.clientonboarding.common.api.ApiSuccess;
import com.brainserve.clientonboarding.common.domain.model.PageSlice;
import com.brainserve.clientonboarding.common.observability.RequestIds;
import com.brainserve.clientonboarding.common.observability.RequestMetadata;
import com.brainserve.clientonboarding.common.security.CurrentPrincipal;
import com.brainserve.clientonboarding.workflow.application.WorkflowTemplateService;
import com.brainserve.clientonboarding.workflow.domain.model.TemplateStep;
import com.brainserve.clientonboarding.workflow.domain.model.TemplateVersion;
import com.brainserve.clientonboarding.workflow.domain.model.WorkflowCondition;
import com.brainserve.clientonboarding.workflow.domain.model.WorkflowTemplate;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class WorkflowController {
    private final WorkflowTemplateService service;
    public WorkflowController(WorkflowTemplateService service) { this.service = service; }

    @GetMapping("/workflow-templates")
    ApiSuccess<List<WorkflowTemplate>> list(@RequestParam(defaultValue = "") String search,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "50") int size,
                                            Authentication authentication) {
        PageSlice<WorkflowTemplate> result = service.list(CurrentPrincipal.require(authentication), search, page, size);
        return new ApiSuccess<>(true, result.items(), result.meta(), RequestIds.currentRequestId());
    }

    @PostMapping("/workflow-templates")
    @ResponseStatus(HttpStatus.CREATED)
    ApiSuccess<WorkflowTemplateService.TemplateBundle> create(@Valid @RequestBody TemplateRequest request,
                                                              Authentication authentication,
                                                              HttpServletRequest servletRequest) {
        return ApiSuccess.of(service.create(CurrentPrincipal.require(authentication),
                new WorkflowTemplateService.TemplateCommand(request.name(), request.description(), request.serviceId()),
                RequestMetadata.from(servletRequest)), RequestIds.currentRequestId());
    }

    @GetMapping("/workflow-templates/{templateId}")
    ApiSuccess<WorkflowTemplate> get(@PathVariable UUID templateId, Authentication authentication) {
        return ApiSuccess.of(service.getTemplate(CurrentPrincipal.require(authentication), templateId),
                RequestIds.currentRequestId());
    }

    @GetMapping("/workflow-templates/{templateId}/versions")
    ApiSuccess<List<TemplateVersion>> versions(@PathVariable UUID templateId, Authentication authentication) {
        return ApiSuccess.of(service.versions(CurrentPrincipal.require(authentication), templateId),
                RequestIds.currentRequestId());
    }

    @PostMapping("/workflow-templates/{templateId}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    ApiSuccess<WorkflowTemplateService.VersionBundle> createVersion(@PathVariable UUID templateId,
                                                                    @RequestBody(required = false)
                                                                    VersionRequest request,
                                                                    Authentication authentication,
                                                                    HttpServletRequest servletRequest) {
        UUID source = request == null ? null : request.sourceVersionId();
        return ApiSuccess.of(service.createVersion(CurrentPrincipal.require(authentication), templateId, source,
                RequestMetadata.from(servletRequest)), RequestIds.currentRequestId());
    }

    @PostMapping("/workflow-templates/{templateId}/archive")
    ApiSuccess<MessageView> archive(@PathVariable UUID templateId, @RequestParam long version,
                                    Authentication authentication, HttpServletRequest servletRequest) {
        service.archive(CurrentPrincipal.require(authentication), templateId, version,
                RequestMetadata.from(servletRequest));
        return ApiSuccess.of(new MessageView("Workflow template archived."), RequestIds.currentRequestId());
    }

    @GetMapping("/workflow-template-versions/{versionId}")
    ApiSuccess<WorkflowTemplateService.VersionBundle> getVersion(@PathVariable UUID versionId,
                                                                 Authentication authentication) {
        return ApiSuccess.of(service.getVersion(CurrentPrincipal.require(authentication), versionId),
                RequestIds.currentRequestId());
    }

    @PutMapping("/workflow-template-versions/{versionId}/steps")
    ApiSuccess<WorkflowTemplateService.VersionBundle> replaceSteps(@PathVariable UUID versionId,
                                                                   @Valid @RequestBody StepsRequest request,
                                                                   Authentication authentication,
                                                                   HttpServletRequest servletRequest) {
        List<WorkflowTemplateService.StepCommand> commands = request.steps().stream().map(this::command).toList();
        return ApiSuccess.of(service.replaceSteps(CurrentPrincipal.require(authentication), versionId,
                request.version(), commands,
                RequestMetadata.from(servletRequest)), RequestIds.currentRequestId());
    }

    @PostMapping("/workflow-template-versions/{versionId}/publish")
    ApiSuccess<WorkflowTemplateService.VersionBundle> publish(@PathVariable UUID versionId,
                                                              @RequestParam long version,
                                                              Authentication authentication,
                                                              HttpServletRequest servletRequest) {
        return ApiSuccess.of(service.publish(CurrentPrincipal.require(authentication), versionId, version,
                RequestMetadata.from(servletRequest)), RequestIds.currentRequestId());
    }

    private WorkflowTemplateService.StepCommand command(StepRequest value) {
        return new WorkflowTemplateService.StepCommand(value.id(), value.stepKey(), value.name(),
                value.description(), value.stepType(), value.displayOrder(), value.required(), value.blocking(),
                value.clientVisible(), value.requiresReview(), value.dependencyMode(), value.condition(),
                value.assignedRole(), value.dueAfterHours(), value.reminderPolicyId(), value.allowSkip(),
                value.allowReopen(), value.configuration(), value.dependencyStepIds());
    }

    public record TemplateRequest(@NotBlank @Size(max = 180) String name,
                                  @Size(max = 1000) String description,
                                  UUID serviceId) { }
    public record VersionRequest(UUID sourceVersionId) { }
    public record StepsRequest(@PositiveOrZero long version,
                               @NotNull @Size(min = 1, max = 200) List<@Valid StepRequest> steps) { }
    public record StepRequest(UUID id,
                              @NotBlank @Size(max = 80) String stepKey,
                              @NotBlank @Size(max = 180) String name,
                              @Size(max = 1000) String description,
                              @NotNull TemplateStep.StepType stepType,
                              @PositiveOrZero int displayOrder,
                              boolean required,
                              boolean blocking,
                              boolean clientVisible,
                              boolean requiresReview,
                              @NotNull TemplateStep.DependencyMode dependencyMode,
                              WorkflowCondition condition,
                              @Size(max = 100) String assignedRole,
                              @Min(0) @Max(87600) Integer dueAfterHours,
                              UUID reminderPolicyId,
                              boolean allowSkip,
                              boolean allowReopen,
                              Map<String, Object> configuration,
                              @Size(max = 200) List<UUID> dependencyStepIds) { }
    public record MessageView(String message) { }
}
