package com.brainserve.clientonboarding.project.api;

import com.brainserve.clientonboarding.common.api.ApiSuccess;
import com.brainserve.clientonboarding.common.api.PageSlice;
import com.brainserve.clientonboarding.common.observability.RequestIds;
import com.brainserve.clientonboarding.common.observability.RequestMetadata;
import com.brainserve.clientonboarding.common.security.CurrentPrincipal;
import com.brainserve.clientonboarding.project.application.ProjectService;
import com.brainserve.clientonboarding.project.domain.model.ActivityEntry;
import com.brainserve.clientonboarding.project.domain.model.ProjectMember;
import com.brainserve.clientonboarding.project.domain.model.ProjectRecord;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ProjectController {
    private final ProjectService service;
    public ProjectController(ProjectService service) { this.service = service; }

    @GetMapping("/projects")
    ApiSuccess<List<ProjectRecord>> list(@RequestParam(defaultValue = "") String search,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(required = false) UUID clientId,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "50") int size,
                                         Authentication authentication) {
        PageSlice<ProjectRecord> result = service.list(CurrentPrincipal.require(authentication), search, status,
                clientId, page, size);
        return new ApiSuccess<>(true, result.items(), result.meta(), RequestIds.currentRequestId());
    }

    @PostMapping("/projects")
    @ResponseStatus(HttpStatus.CREATED)
    ApiSuccess<ProjectRecord> create(@Valid @RequestBody ProjectRequest request, Authentication authentication,
                                     HttpServletRequest servletRequest) {
        return ApiSuccess.of(service.create(CurrentPrincipal.require(authentication), command(request),
                RequestMetadata.from(servletRequest)), RequestIds.currentRequestId());
    }

    @GetMapping("/projects/{projectId}")
    ApiSuccess<ProjectRecord> get(@PathVariable UUID projectId, Authentication authentication) {
        return ApiSuccess.of(service.get(CurrentPrincipal.require(authentication), projectId),
                RequestIds.currentRequestId());
    }

    @PatchMapping("/projects/{projectId}")
    ApiSuccess<ProjectRecord> update(@PathVariable UUID projectId, @Valid @RequestBody ProjectRequest request,
                                     Authentication authentication, HttpServletRequest servletRequest) {
        return ApiSuccess.of(service.update(CurrentPrincipal.require(authentication), projectId, command(request),
                RequestMetadata.from(servletRequest)), RequestIds.currentRequestId());
    }

    @PostMapping("/projects/{projectId}/{action:hold|resume|complete|cancel|archive}")
    ApiSuccess<ProjectRecord> transition(@PathVariable UUID projectId, @PathVariable String action,
                                         @RequestParam @PositiveOrZero long version,
                                         Authentication authentication,
                                         HttpServletRequest servletRequest) {
        return ApiSuccess.of(service.transition(CurrentPrincipal.require(authentication), projectId, action,
                version, RequestMetadata.from(servletRequest)), RequestIds.currentRequestId());
    }

    @GetMapping("/projects/{projectId}/members")
    ApiSuccess<List<ProjectMember>> members(@PathVariable UUID projectId, Authentication authentication) {
        return ApiSuccess.of(service.members(CurrentPrincipal.require(authentication), projectId),
                RequestIds.currentRequestId());
    }

    @PostMapping("/projects/{projectId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    ApiSuccess<ProjectMember> addMember(@PathVariable UUID projectId, @Valid @RequestBody MemberRequest request,
                                        Authentication authentication, HttpServletRequest servletRequest) {
        return ApiSuccess.of(service.addMember(CurrentPrincipal.require(authentication), projectId,
                new ProjectService.MemberCommand(request.membershipId(), request.assignmentRole()),
                RequestMetadata.from(servletRequest)), RequestIds.currentRequestId());
    }

    @DeleteMapping("/projects/{projectId}/members/{memberId}")
    ApiSuccess<MessageView> removeMember(@PathVariable UUID projectId, @PathVariable UUID memberId,
                                         Authentication authentication, HttpServletRequest servletRequest) {
        service.removeMember(CurrentPrincipal.require(authentication), projectId, memberId,
                RequestMetadata.from(servletRequest));
        return ApiSuccess.of(new MessageView("Project member removed."), RequestIds.currentRequestId());
    }

    @GetMapping("/projects/{projectId}/activities")
    ApiSuccess<List<ActivityEntry>> activity(@PathVariable UUID projectId,
                                             @RequestParam(defaultValue = "50") int limit,
                                             Authentication authentication) {
        return ApiSuccess.of(service.activity(CurrentPrincipal.require(authentication), projectId, limit),
                RequestIds.currentRequestId());
    }

    private ProjectService.ProjectCommand command(ProjectRequest request) {
        return new ProjectService.ProjectCommand(request.clientId(), request.serviceId(), request.name(),
                request.description(), request.valueMinor(), request.currencyCode(), request.targetStartDate(),
                request.version());
    }

    public record ProjectRequest(@NotNull UUID clientId, @NotNull UUID serviceId,
                                 @NotBlank @Size(max = 180) String name,
                                 @Size(max = 2000) String description,
                                 @PositiveOrZero Long valueMinor, @Size(max = 3) String currencyCode,
                                 LocalDate targetStartDate, @PositiveOrZero long version) { }
    public record MemberRequest(@NotNull UUID membershipId,
                                @NotBlank @Size(max = 80) String assignmentRole) { }
    public record MessageView(String message) { }
}
