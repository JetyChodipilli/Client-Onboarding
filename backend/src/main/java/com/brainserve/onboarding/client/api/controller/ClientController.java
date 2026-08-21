package com.brainserve.onboarding.client.api.controller;

import com.brainserve.onboarding.client.api.request.ArchiveClientRequest;
import com.brainserve.onboarding.client.api.request.CreateClientContactRequest;
import com.brainserve.onboarding.client.api.request.CreateClientRequest;
import com.brainserve.onboarding.client.api.request.UpdateClientContactRequest;
import com.brainserve.onboarding.client.api.request.UpdateClientRequest;
import com.brainserve.onboarding.client.api.response.ClientContactResponse;
import com.brainserve.onboarding.client.api.response.ClientResponse;
import com.brainserve.onboarding.client.application.service.ClientService;
import com.brainserve.onboarding.client.domain.model.ClientStatus;
import com.brainserve.onboarding.common.api.ActivityResponse;
import com.brainserve.onboarding.common.api.ApiResponse;
import com.brainserve.onboarding.common.observability.RequestContext;
import com.brainserve.onboarding.common.security.TenantPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
public class ClientController {
    private final ClientService service;

    public ClientController(ClientService service) { this.service = service; }

    @PostMapping("/clients")
    @PreAuthorize("hasAuthority('CLIENT_CREATE')")
    ApiResponse<ClientResponse> create(@AuthenticationPrincipal TenantPrincipal principal,
                                       @Valid @RequestBody CreateClientRequest body,
                                       HttpServletRequest request) {
        return ApiResponse.success(service.create(principal, body, request), RequestContext.requestId());
    }

    @GetMapping("/clients")
    @PreAuthorize("hasAuthority('CLIENT_READ')")
    ApiResponse<List<ClientResponse>> list(@AuthenticationPrincipal TenantPrincipal principal,
                                           @RequestParam(defaultValue = "0") @Min(0) int page,
                                           @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size,
                                           @RequestParam(required = false) ClientStatus status,
                                           @RequestParam(required = false) @Size(max = 120) String query,
                                           @RequestParam(defaultValue = "false") boolean includeArchived) {
        var result = service.list(principal, page, size, status, query, includeArchived);
        return ApiResponse.success(result.items(), meta(result.page(), result.size(), result.totalElements(), result.totalPages()), RequestContext.requestId());
    }

    @GetMapping("/clients/{clientId}")
    @PreAuthorize("hasAuthority('CLIENT_READ')")
    ApiResponse<ClientResponse> get(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID clientId) {
        return ApiResponse.success(service.get(principal, clientId), RequestContext.requestId());
    }

    @PatchMapping("/clients/{clientId}")
    @PreAuthorize("hasAuthority('CLIENT_UPDATE')")
    ApiResponse<ClientResponse> update(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID clientId,
                                       @Valid @RequestBody UpdateClientRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.update(principal, clientId, body, request), RequestContext.requestId());
    }

    @PostMapping("/clients/{clientId}/archive")
    @PreAuthorize("hasAuthority('CLIENT_UPDATE')")
    ApiResponse<ClientResponse> archive(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID clientId,
                                        @Valid @RequestBody ArchiveClientRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.archive(principal, clientId, body, request), RequestContext.requestId());
    }

    @PostMapping("/clients/{clientId}/contacts")
    @PreAuthorize("hasAuthority('CLIENT_UPDATE')")
    ApiResponse<ClientContactResponse> createContact(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID clientId,
                                                     @Valid @RequestBody CreateClientContactRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.createContact(principal, clientId, body, request), RequestContext.requestId());
    }

    @GetMapping("/clients/{clientId}/contacts")
    @PreAuthorize("hasAuthority('CLIENT_READ')")
    ApiResponse<List<ClientContactResponse>> contacts(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID clientId,
                                                      @RequestParam(defaultValue = "0") @Min(0) int page,
                                                      @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        var result = service.listContacts(principal, clientId, page, size);
        return ApiResponse.success(result.items(), meta(result.page(), result.size(), result.totalElements(), result.totalPages()), RequestContext.requestId());
    }

    @GetMapping("/clients/{clientId}/activity")
    @PreAuthorize("hasAuthority('CLIENT_READ')")
    ApiResponse<List<ActivityResponse>> activity(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID clientId,
                                                 @RequestParam(defaultValue = "0") @Min(0) int page,
                                                 @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        var result = service.activity(principal, clientId, page, size);
        return ApiResponse.success(result.items(), meta(result.page(), result.size(), result.totalElements(), result.totalPages()), RequestContext.requestId());
    }

    @PatchMapping("/client-contacts/{contactId}")
    @PreAuthorize("hasAuthority('CLIENT_UPDATE')")
    ApiResponse<ClientContactResponse> updateContact(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable UUID contactId,
                                                     @Valid @RequestBody UpdateClientContactRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.updateContact(principal, contactId, body, request), RequestContext.requestId());
    }

    private static Map<String, Object> meta(int page, int size, long totalElements, int totalPages) {
        return Map.of("page", page, "size", size, "totalElements", totalElements, "totalPages", totalPages);
    }
}
