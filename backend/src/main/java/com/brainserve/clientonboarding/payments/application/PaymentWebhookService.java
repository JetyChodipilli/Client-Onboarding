package com.brainserve.clientonboarding.payments.application;

import com.brainserve.clientonboarding.billing.application.*;
import com.brainserve.clientonboarding.common.error.DomainException;
import com.brainserve.clientonboarding.payments.infrastructure.persistence.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PaymentWebhookService {
    private final PaymentProvider provider;private final PaymentRepository repository;private final PaymentEvidenceService evidence;private final RefundCommands refunds;private final BillingLedger ledger;private final ObjectMapper json;private final Clock clock;private final TransactionTemplate tx;
    public PaymentWebhookService(PaymentProvider provider,PaymentRepository repository,PaymentEvidenceService evidence,RefundCommands refunds,BillingLedger ledger,ObjectMapper json,Clock clock,org.springframework.transaction.PlatformTransactionManager manager){this.provider=provider;this.repository=repository;this.evidence=evidence;this.refunds=refunds;this.ledger=ledger;this.json=json;this.clock=clock;tx=new TransactionTemplate(manager);}
    public void receive(UUID org,byte[] raw,String signature,String eventId){
        if(raw.length>1048576)throw new DomainException("PAYLOAD_TOO_LARGE","Webhook body is too large.",HttpStatus.PAYLOAD_TOO_LARGE);
        if(!provider.verifyWebhook(org,raw,signature))throw new DomainException("INVALID_WEBHOOK_SIGNATURE","Webhook signature could not be verified.",HttpStatus.UNAUTHORIZED);
        if(eventId==null || !eventId.matches("[A-Za-z0-9._:-]{1,160}"))throw BillingErrors.invalid("A valid Razorpay event ID is required.");
        String hash=BillingFingerprint.bytes(raw);if(repository.eventExists(org,eventId,hash))return;
        String type,id;
        try {var root=json.readTree(raw);type=root.path("event").asText();if(type.length()>80 || type.isBlank())throw BillingErrors.invalid("Invalid webhook event.");id=root.path("payload").path(type.startsWith("refund.")?"refund":"payment").path("entity").path("id").asText();}
        catch(java.io.IOException e){throw BillingErrors.invalid("Invalid webhook JSON.");}
        boolean paymentEvent=Set.of("payment.authorized","payment.captured","payment.failed").contains(type);
        boolean refundEvent=Set.of("refund.created","refund.processed","refund.failed").contains(type);
        PaymentProvider.Refund refund=refundEvent?provider.refund(org,id):null;
        PaymentProvider.Payment payment=paymentEvent?provider.payment(org,id):refund!=null?provider.payment(org,refund.paymentId()):null;
        // Recover a create-order response lost to a timeout using the provider's immutable receipt.
        if(payment!=null && repository.order(org,payment.orderId()).isEmpty()){
            var order=provider.order(org,payment.orderId());
            try {UUID local=UUID.fromString(order.receipt());if(repository.session(org,local).isPresent())evidence.attach(org,local,order,null);}
            catch(IllegalArgumentException ignored){/* Payments unrelated to this application are acknowledged below. */}
        }
        tx.executeWithoutResult(status->{
            ledger.lockCommands(org);if(repository.eventExists(org,eventId,hash))return;
            boolean known=payment!=null && repository.order(org,payment.orderId()).isPresent();
            if(known){evidence.apply(org,payment,null,"WEBHOOK");if(refund!=null)refunds.apply(org,refund,null,null);}
            repository.event(org,eventId,type,hash,known,clock.instant());
        });
    }
}
