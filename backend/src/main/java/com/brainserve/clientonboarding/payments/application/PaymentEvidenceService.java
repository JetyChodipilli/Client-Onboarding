package com.brainserve.clientonboarding.payments.application;

import static com.brainserve.clientonboarding.payments.domain.model.PaymentModels.*;
import com.brainserve.clientonboarding.billing.application.*;
import com.brainserve.clientonboarding.billing.domain.model.BillingModels.Invoice;
import com.brainserve.clientonboarding.payments.infrastructure.persistence.PaymentRepository;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Atomic provider evidence -> transaction -> invoice -> workflow -> audit/outbox. */
@Service
public class PaymentEvidenceService {
    private final PaymentRepository repository;private final BillingLedger ledger;private final Clock clock;
    public PaymentEvidenceService(PaymentRepository repository,BillingLedger ledger,Clock clock){this.repository=repository;this.ledger=ledger;this.clock=clock;}
    @Transactional
    public Session attach(UUID org,UUID sessionId,PaymentProvider.Order order,UUID actor){
        ledger.lockCommands(org);var s=repository.session(org,sessionId).orElseThrow(BillingErrors::missing);var i=ledger.lock(org,s.invoiceId());
        if(!s.id().toString().equals(order.receipt()) || order.amountMinor()!=s.amountMinor() || !s.currency().equals(order.currency()) || !order.id().matches("order_[A-Za-z0-9]{1,80}"))throw BillingErrors.state("Provider order does not match this payment attempt.");
        if(s.providerOrderId()!=null && !s.providerOrderId().equals(order.id()))throw BillingErrors.state("This payment already has a different provider order.");
        if(s.status()==SessionStatus.FAILED)throw BillingErrors.state("This payment request was rejected.");
        if(s.providerOrderId()==null)repository.sessionState(s,order.id(),SessionStatus.READY,actor,clock.instant());
        return repository.session(org,sessionId).orElseThrow();
    }
    @Transactional
    public void orderFailure(UUID org,UUID sessionId,boolean definite,UUID actor){
        ledger.lockCommands(org);var s=repository.session(org,sessionId).orElseThrow(BillingErrors::missing);var i=ledger.lock(org,s.invoiceId());
        if(s.status()!=SessionStatus.CREATING)return;
        repository.sessionState(s,null,definite?SessionStatus.FAILED:SessionStatus.UNKNOWN,actor,clock.instant());
        recalculate(i,actor,definite?"PAYMENT_ORDER_REJECTED":"PAYMENT_ORDER_UNCONFIRMED","PROVIDER");
    }
    @Transactional
    public void apply(UUID org,PaymentProvider.Payment payment,UUID actor,String source){
        ledger.lockCommands(org);var s=repository.order(org,payment.orderId()).orElseThrow(BillingErrors::missing);var i=ledger.lock(org,s.invoiceId());applyLocked(i,s,payment,actor,source);
    }
    public void applyLocked(Invoice i,Session s,PaymentProvider.Payment payment,UUID actor,String source){
        UUID org=i.organizationId();
        if(!payment.id().matches("pay_[A-Za-z0-9]{1,80}") || !s.providerOrderId().equals(payment.orderId()) || payment.amountMinor()!=s.amountMinor() || !s.currency().equals(payment.currency()) || payment.refundedMinor()>payment.amountMinor())throw BillingErrors.state("Provider payment does not match the invoice order.");
        TransactionStatus status=switch(payment.status()){case "created" -> TransactionStatus.INITIATED;case "authorized" -> TransactionStatus.AUTHORIZED;case "captured" -> TransactionStatus.CAPTURED;case "refunded" -> TransactionStatus.REFUNDED;case "failed" -> TransactionStatus.FAILED;default -> throw BillingErrors.state("Unknown provider payment state.");};
        if((status.captured() || payment.refundedMinor()>0) && !payment.captured())throw BillingErrors.state("The provider has not confirmed capture.");
        var existing=repository.providerTransaction(org,payment.id());
        if(existing.isPresent() && !Objects.equals(existing.get().paymentId(),s.id()))throw BillingErrors.state("Provider payment belongs to a different order.");
        long refunded=Math.max(payment.refundedMinor(),existing.map(Transaction::refundedMinor).orElse(0L));
        if(existing.isPresent() && existing.get().status().captured())status=TransactionStatus.CAPTURED;
        else if(existing.isPresent() && existing.get().status()==TransactionStatus.AUTHORIZED && status==TransactionStatus.INITIATED)status=TransactionStatus.AUTHORIZED;
        if(status.captured())status=refunded==payment.amountMinor()?TransactionStatus.REFUNDED:refunded>0?TransactionStatus.PARTIALLY_REFUNDED:TransactionStatus.CAPTURED;
        boolean changed=existing.isEmpty() || existing.get().status()!=status || existing.get().refundedMinor()!=refunded;
        if(existing.isEmpty())repository.insert(new Transaction(UUID.randomUUID(),org,i.id(),s.id(),"RAZORPAY",payment.id(),status,payment.amountMinor(),refunded,payment.currency(),null,null,clock.instant(),0),null,null,actor);
        else if(changed)repository.transactionState(existing.get(),status,refunded,actor,clock.instant());
        if(status.captured() && s.status()!=SessionStatus.SETTLED)repository.sessionState(s,s.providerOrderId(),SessionStatus.SETTLED,actor,clock.instant());
        if(changed)recalculate(i,actor,"PAYMENT_EVIDENCE_RECORDED",source);else ledger.sync(i,actor);
    }
    public void recalculate(Invoice i,UUID actor,String event,String source){var b=repository.balances(i.organizationId(),i.id());ledger.balances(i,b.captured(),b.refunded(),b.reserved(),actor,event,source);}
}
