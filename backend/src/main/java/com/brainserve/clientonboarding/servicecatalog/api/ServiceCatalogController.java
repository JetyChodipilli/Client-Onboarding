package com.brainserve.clientonboarding.servicecatalog.api;

import com.brainserve.clientonboarding.common.api.ApiSuccess;
import com.brainserve.clientonboarding.common.domain.model.PageSlice;
import com.brainserve.clientonboarding.common.observability.RequestIds;
import com.brainserve.clientonboarding.common.observability.RequestMetadata;
import com.brainserve.clientonboarding.common.security.CurrentPrincipal;
import com.brainserve.clientonboarding.servicecatalog.application.ServiceCatalogService;
import com.brainserve.clientonboarding.servicecatalog.domain.model.ServiceDefinition;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
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
@RequestMapping("/api/v1/services")
public class ServiceCatalogController {
    private final ServiceCatalogService service;
    public ServiceCatalogController(ServiceCatalogService service) { this.service = service; }

    @GetMapping
    ApiSuccess<List<ServiceDefinition>> list(@RequestParam(defaultValue = "") String search,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "50") int size,
                                             Authentication authentication) {
        PageSlice<ServiceDefinition> result = service.list(CurrentPrincipal.require(authentication), search,
                status, page, size);
        return new ApiSuccess<>(true, result.items(), result.meta(), RequestIds.currentRequestId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ApiSuccess<ServiceDefinition> create(@Valid @RequestBody ServiceRequest request,
                                         Authentication authentication, HttpServletRequest servletRequest) {
        return ApiSuccess.of(service.create(CurrentPrincipal.require(authentication), command(request),
                RequestMetadata.from(servletRequest)), RequestIds.currentRequestId());
    }

    @GetMapping("/{serviceId}")
    ApiSuccess<ServiceDefinition> get(@PathVariable UUID serviceId, Authentication authentication) {
        return ApiSuccess.of(service.get(CurrentPrincipal.require(authentication), serviceId),
                RequestIds.currentRequestId());
    }

    @PatchMapping("/{serviceId}")
    ApiSuccess<ServiceDefinition> update(@PathVariable UUID serviceId, @Valid @RequestBody ServiceRequest request,
                                         Authentication authentication, HttpServletRequest servletRequest) {
        return ApiSuccess.of(service.update(CurrentPrincipal.require(authentication), serviceId, command(request),
                RequestMetadata.from(servletRequest)), RequestIds.currentRequestId());
    }

    @PostMapping("/{serviceId}/archive")
    ApiSuccess<MessageView> archive(@PathVariable UUID serviceId,
                                    @RequestParam @PositiveOrZero long version,
                                    Authentication authentication, HttpServletRequest servletRequest) {
        service.archive(CurrentPrincipal.require(authentication), serviceId, version,
                RequestMetadata.from(servletRequest));
        return ApiSuccess.of(new MessageView("Service archived."), RequestIds.currentRequestId());
    }

    private ServiceCatalogService.ServiceCommand command(ServiceRequest request) {
        return new ServiceCatalogService.ServiceCommand(request.code(), request.name(), request.description(),
                request.status(), request.version());
    }

    public record ServiceRequest(@NotBlank @Size(max = 80) String code,
                                 @NotBlank @Size(max = 160) String name,
                                 @Size(max = 1000) String description,
                                 @NotBlank @Size(max = 24) String status,
                                 @PositiveOrZero long version) { }
    public record MessageView(String message) { }
}
