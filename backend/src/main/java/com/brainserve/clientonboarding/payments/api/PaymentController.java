package com.brainserve.clientonboarding.payments.api;

import com.brainserve.clientonboarding.payments.application.*;
import com.brainserve.clientonboarding.payments.domain.model.PaymentModels.*;
import com.brainserve.clientonboarding.common.api.ApiSuccess;
import com.brainserve.clientonboarding.common.observability.RequestIds;
import com.brainserve.clientonboarding.common.security.CurrentPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1")
public class PaymentController {
    private final PaymentService payments;private final PaymentCommands commands;private final RefundService refunds;
    public PaymentController(PaymentService payments,PaymentCommands commands,RefundService refunds){this.payments=payments;this.commands=commands;this.refunds=refunds;}
    @GetMapping("/invoices/{id}/payments")
    ApiSuccess<PaymentService.History> history(@PathVariable UUID id,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,Authentication auth){return ok(payments.history(CurrentPrincipal.require(auth),id,false,page,size));}
    @GetMapping("/client-portal/invoices/{id}/payments")
    ApiSuccess<PaymentService.History> clientHistory(@PathVariable UUID id,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,Authentication auth){return ok(payments.history(CurrentPrincipal.require(auth),id,true,page,size));}
    @PostMapping("/client-portal/invoices/{id}/checkout")
    ApiSuccess<PaymentService.Checkout> checkout(@PathVariable UUID id,@Valid @RequestBody Amount r,@RequestHeader("Idempotency-Key")String key,Authentication auth){return ok(payments.checkout(CurrentPrincipal.require(auth),id,r.amountMinor(),key));}
    @PostMapping("/client-portal/payments/{id}/confirm")
    ApiSuccess<Map<String,String>> confirm(@PathVariable UUID id,@Valid @RequestBody Confirm r,Authentication auth){payments.confirm(CurrentPrincipal.require(auth),id,r.paymentId(),r.signature());return ok(Map.of("message","Provider evidence checked. Refresh the invoice for its current status."));}
    @PostMapping("/payments/{id}/reconcile")
    ApiSuccess<Map<String,String>> reconcile(@PathVariable UUID id,@RequestBody Reconcile r,Authentication auth){payments.reconcile(CurrentPrincipal.require(auth),id,r.providerId());return ok(Map.of("message","Payment reconciliation completed."));}
    @PostMapping("/invoices/{id}/sync-workflow")
    ApiSuccess<Map<String,String>> sync(@PathVariable UUID id,Authentication auth){payments.sync(CurrentPrincipal.require(auth),id);return ok(Map.of("message","Workflow readiness recalculated."));}
    @PostMapping("/invoices/{id}/manual-payments")
    ApiSuccess<Transaction> manual(@PathVariable UUID id,@Valid @RequestBody Manual r,@RequestHeader("Idempotency-Key")String key,Authentication auth){return ok(commands.manual(CurrentPrincipal.require(auth),id,r.amountMinor(),r.reference(),r.reason(),key));}
    @GetMapping("/payment-transactions/{id}/refunds")
    ApiSuccess<List<Refund>> refunds(@PathVariable UUID id,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,Authentication auth){var result=refunds.list(CurrentPrincipal.require(auth),id,page,size);return new ApiSuccess<>(true,result.items(),result.meta(),RequestIds.currentRequestId());}
    @PostMapping("/payment-transactions/{id}/refunds")
    ApiSuccess<Refund> refund(@PathVariable UUID id,@Valid @RequestBody RefundRequest r,@RequestHeader("Idempotency-Key")String key,Authentication auth){return ok(refunds.create(CurrentPrincipal.require(auth),id,r.amountMinor(),r.reason(),key));}
    @PostMapping("/refunds/{id}/reconcile")
    ApiSuccess<Refund> reconcileRefund(@PathVariable UUID id,@RequestBody Reconcile r,Authentication auth){return ok(refunds.reconcile(CurrentPrincipal.require(auth),id,r.providerId()));}
    private <T> ApiSuccess<T> ok(T value){return ApiSuccess.of(value,RequestIds.currentRequestId());}
    public record Amount(@Min(100) @Max(100000000000L)long amountMinor){}
    public record Confirm(@NotBlank @Pattern(regexp="pay_[A-Za-z0-9]{1,80}")String paymentId,@NotBlank @Pattern(regexp="[a-fA-F0-9]{64}")String signature){}
    public record Reconcile(@Size(max=100)String providerId){}
    public record Manual(@Min(1) @Max(100000000000L)long amountMinor,@NotBlank @Size(max=120)String reference,@NotBlank @Size(max=2000)String reason){}
    public record RefundRequest(@Min(1) @Max(100000000000L)long amountMinor,@NotBlank @Size(max=2000)String reason){}
}
