package com.brainserve.clientonboarding.billing.api;

import com.brainserve.clientonboarding.billing.application.InvoiceService;
import com.brainserve.clientonboarding.billing.domain.model.BillingModels.Status;
import com.brainserve.clientonboarding.common.api.ApiSuccess;
import com.brainserve.clientonboarding.common.observability.RequestIds;
import com.brainserve.clientonboarding.common.security.CurrentPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1")
public class InvoiceController {
    private final InvoiceService invoices;
    public InvoiceController(InvoiceService invoices){this.invoices=invoices;}
    @GetMapping("/invoices")
    ApiSuccess<List<InvoiceService.View>> list(@RequestParam(required=false)UUID projectId,@RequestParam(defaultValue="")String search,@RequestParam(required=false)Status status,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,Authentication auth){var r=invoices.list(CurrentPrincipal.require(auth),projectId,search,status,page,size);return new ApiSuccess<>(true,r.items(),r.meta(),RequestIds.currentRequestId());}
    @PostMapping("/invoices") @ResponseStatus(HttpStatus.CREATED)
    ApiSuccess<InvoiceService.View> create(@Valid @RequestBody InvoiceService.Create r,@RequestHeader("Idempotency-Key")String key,Authentication auth){return ok(invoices.create(CurrentPrincipal.require(auth),r,key));}
    @GetMapping("/invoices/by-step/{stepId}")
    ApiSuccess<InvoiceService.View> step(@PathVariable UUID stepId,Authentication auth){return ok(invoices.internalStep(CurrentPrincipal.require(auth),stepId));}
    @GetMapping("/invoices/{id}")
    ApiSuccess<InvoiceService.View> get(@PathVariable UUID id,Authentication auth){return ok(invoices.get(CurrentPrincipal.require(auth),id,false));}
    @PostMapping("/invoices/{id}/send")
    ApiSuccess<InvoiceService.View> send(@PathVariable UUID id,@Valid @RequestBody Version r,Authentication auth){return ok(invoices.publish(CurrentPrincipal.require(auth),id,r.version()));}
    @PostMapping("/invoices/{id}/close")
    ApiSuccess<InvoiceService.View> close(@PathVariable UUID id,@Valid @RequestBody Close r,Authentication auth){return ok(invoices.close(CurrentPrincipal.require(auth),id,r.version(),r.status(),r.reason()));}
    @GetMapping("/client-portal/projects/{projectId}/payments/{stepId}")
    ApiSuccess<InvoiceService.View> client(@PathVariable UUID projectId,@PathVariable UUID stepId,Authentication auth){return ok(invoices.forStep(CurrentPrincipal.require(auth),projectId,stepId));}
    @PostMapping("/client-portal/invoices/{id}/viewed")
    ApiSuccess<InvoiceService.View> viewed(@PathVariable UUID id,Authentication auth){return ok(invoices.viewed(CurrentPrincipal.require(auth),id));}
    private <T> ApiSuccess<T> ok(T value){return ApiSuccess.of(value,RequestIds.currentRequestId());}
    public record Version(@PositiveOrZero long version){}
    public record Close(@PositiveOrZero long version,@NotNull Status status,@NotBlank @Size(max=2000)String reason){}
}
