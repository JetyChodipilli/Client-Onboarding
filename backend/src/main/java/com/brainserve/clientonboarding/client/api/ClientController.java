package com.brainserve.clientonboarding.client.api;

import com.brainserve.clientonboarding.client.application.ClientService;
import com.brainserve.clientonboarding.client.domain.model.ClientContact;
import com.brainserve.clientonboarding.client.domain.model.ClientRecord;
import com.brainserve.clientonboarding.common.api.ApiSuccess;
import com.brainserve.clientonboarding.common.api.PageSlice;
import com.brainserve.clientonboarding.common.observability.RequestIds;
import com.brainserve.clientonboarding.common.observability.RequestMetadata;
import com.brainserve.clientonboarding.common.security.CurrentPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
@RequestMapping("/api/v1")
public class ClientController {
    private final ClientService service;
    public ClientController(ClientService service) { this.service = service; }

    @GetMapping("/clients")
    ApiSuccess<List<ClientRecord>> list(@RequestParam(defaultValue = "") String search,
                                        @RequestParam(required = false) String status,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "50") int size,
                                        Authentication authentication) {
        return page(service.list(CurrentPrincipal.require(authentication), search, status, page, size));
    }

    @PostMapping("/clients")
    @ResponseStatus(HttpStatus.CREATED)
    ApiSuccess<ClientRecord> create(@Valid @RequestBody ClientRequest request, Authentication authentication,
                                    HttpServletRequest servletRequest) {
        return ApiSuccess.of(service.create(CurrentPrincipal.require(authentication), command(request),
                RequestMetadata.from(servletRequest)), RequestIds.currentRequestId());
    }

    @GetMapping("/clients/{clientId}")
    ApiSuccess<ClientRecord> get(@PathVariable UUID clientId, Authentication authentication) {
        return ApiSuccess.of(service.get(CurrentPrincipal.require(authentication), clientId),
                RequestIds.currentRequestId());
    }

    @PatchMapping("/clients/{clientId}")
    ApiSuccess<ClientRecord> update(@PathVariable UUID clientId, @Valid @RequestBody ClientRequest request,
                                    Authentication authentication, HttpServletRequest servletRequest) {
        return ApiSuccess.of(service.update(CurrentPrincipal.require(authentication), clientId, command(request),
                RequestMetadata.from(servletRequest)), RequestIds.currentRequestId());
    }

    @PostMapping("/clients/{clientId}/archive")
    ApiSuccess<MessageView> archive(@PathVariable UUID clientId, @RequestParam @PositiveOrZero long version,
                                    Authentication authentication, HttpServletRequest servletRequest) {
        service.archive(CurrentPrincipal.require(authentication), clientId, version,
                RequestMetadata.from(servletRequest));
        return ApiSuccess.of(new MessageView("Client archived."), RequestIds.currentRequestId());
    }

    @GetMapping("/clients/{clientId}/contacts")
    ApiSuccess<List<ClientContact>> contacts(@PathVariable UUID clientId, Authentication authentication) {
        return ApiSuccess.of(service.contacts(CurrentPrincipal.require(authentication), clientId),
                RequestIds.currentRequestId());
    }

    @PostMapping("/clients/{clientId}/contacts")
    @ResponseStatus(HttpStatus.CREATED)
    ApiSuccess<ClientContact> createContact(@PathVariable UUID clientId,
                                            @Valid @RequestBody ContactRequest request,
                                            Authentication authentication,
                                            HttpServletRequest servletRequest) {
        return ApiSuccess.of(service.createContact(CurrentPrincipal.require(authentication), clientId,
                contactCommand(request), RequestMetadata.from(servletRequest)), RequestIds.currentRequestId());
    }

    @PatchMapping("/client-contacts/{contactId}")
    ApiSuccess<ClientContact> updateContact(@PathVariable UUID contactId,
                                            @Valid @RequestBody ContactRequest request,
                                            Authentication authentication,
                                            HttpServletRequest servletRequest) {
        return ApiSuccess.of(service.updateContact(CurrentPrincipal.require(authentication), contactId,
                contactCommand(request), RequestMetadata.from(servletRequest)), RequestIds.currentRequestId());
    }

    @PostMapping("/client-contacts/{contactId}/archive")
    ApiSuccess<MessageView> archiveContact(@PathVariable UUID contactId,
                                           @RequestParam @PositiveOrZero long version,
                                           Authentication authentication, HttpServletRequest servletRequest) {
        service.archiveContact(CurrentPrincipal.require(authentication), contactId, version,
                RequestMetadata.from(servletRequest));
        return ApiSuccess.of(new MessageView("Contact archived."), RequestIds.currentRequestId());
    }

    private ClientService.ClientCommand command(ClientRequest request) {
        return new ClientService.ClientCommand(request.name(), request.legalName(), request.status(),
                request.website(), request.email(), request.phone(), request.notes(), request.version());
    }

    private ClientService.ContactCommand contactCommand(ContactRequest request) {
        return new ClientService.ContactCommand(request.name(), request.email(), request.phone(),
                request.jobTitle(), request.primary(), request.version());
    }

    private <T> ApiSuccess<List<T>> page(PageSlice<T> value) {
        return new ApiSuccess<>(true, value.items(), value.meta(), RequestIds.currentRequestId());
    }

    public record ClientRequest(@NotBlank @Size(max = 160) String name,
                                @Size(max = 200) String legalName,
                                @NotBlank @Size(max = 24) String status,
                                @Size(max = 500) String website,
                                @Email @Size(max = 254) String email,
                                @Size(max = 50) String phone,
                                @Size(max = 2000) String notes,
                                @PositiveOrZero long version) { }
    public record ContactRequest(@NotBlank @Size(max = 160) String name,
                                 @NotBlank @Email @Size(max = 254) String email,
                                 @Size(max = 50) String phone,
                                 @Size(max = 120) String jobTitle,
                                 boolean primary,
                                 @PositiveOrZero long version) { }
    public record MessageView(String message) { }
}
