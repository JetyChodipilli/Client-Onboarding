package com.brainserve.clientonboarding.payments.application;

import static com.brainserve.clientonboarding.payments.domain.model.PaymentModels.*;
import com.brainserve.clientonboarding.billing.application.*;
import com.brainserve.clientonboarding.common.security.TenantPrincipal;
import com.brainserve.clientonboarding.payments.infrastructure.persistence.PaymentRepository;
import java.time.Clock;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefundCommands {
    private final PaymentRepository repository;private final BillingLedger ledger;private final PaymentEvidenceService evidence;private final BillingFingerprint fingerprint;private final Clock clock;
    public RefundCommands(PaymentRepository repository,BillingLedger ledger,PaymentEvidenceService evidence,BillingFingerprint fingerprint,Clock clock){this.repository=repository;this.ledger=ledger;this.evidence=evidence;this.fingerprint=fingerprint;this.clock=clock;}
    @PreAuthorize("hasAuthority('PAYMENT_REFUND')") @Transactional
    public Prepared prepare(TenantPrincipal p,UUID transaction,long amount,String reason,String key){
        BillingErrors.key(key);reason=BillingErrors.reason(reason);ledger.lockCommands(p.organizationId());
        var t=repository.transaction(p.organizationId(),transaction).orElseThrow(BillingErrors::missing);var i=ledger.lock(p.organizationId(),t.invoiceId());
        String hash=fingerprint.of(List.of(transaction,amount,reason));var duplicate=repository.refundKey(p.organizationId(),key,hash);if(duplicate.isPresent())return new Prepared(duplicate.get(),t,false);
        if(!t.status().captured() || amount<1 || amount>t.amountMinor()-Math.max(t.refundedMinor(),repository.processedRefunds(p.organizationId(),t.id()))-repository.pendingRefunds(p.organizationId(),t.id()))throw BillingErrors.state("Refund exceeds the captured amount remaining after completed and pending refunds.");
        var r=new Refund(UUID.randomUUID(),p.organizationId(),transaction,null,amount,t.provider().equals("MANUAL")?RefundStatus.PROCESSED:RefundStatus.CREATING,reason,key,hash,clock.instant(),0);repository.insert(r,p.userId());
        if(t.provider().equals("MANUAL")){
            long refunded=t.refundedMinor()+amount;repository.transactionState(t,refunded==t.amountMinor()?TransactionStatus.REFUNDED:TransactionStatus.PARTIALLY_REFUNDED,refunded,p.userId(),clock.instant());evidence.recalculate(i,p.userId(),"MANUAL_REFUND_RECORDED","MANUAL_OVERRIDE");
        }else ledger.event(i,"REFUND_REQUESTED",p.userId(),"API");
        return new Prepared(r,t,true);
    }
    @Transactional
    public void failure(UUID org,UUID id,boolean definite,UUID actor){ledger.lockCommands(org);var r=repository.refund(org,id).orElseThrow(BillingErrors::missing);var t=repository.transaction(org,r.transactionId()).orElseThrow();var i=ledger.lock(org,t.invoiceId());if(r.status()!=RefundStatus.CREATING)return;repository.refundState(r,null,definite?RefundStatus.FAILED:RefundStatus.UNKNOWN,actor,clock.instant());ledger.event(i,definite?"REFUND_REJECTED":"REFUND_UNCONFIRMED",actor,"PROVIDER");}
    @Transactional
    public Refund apply(UUID org,PaymentProvider.Refund fact,UUID expectedId,UUID actor){
        ledger.lockCommands(org);var t=repository.providerTransaction(org,fact.paymentId()).orElseThrow(BillingErrors::missing);var i=ledger.lock(org,t.invoiceId());
        if(!fact.id().matches("rfnd_[A-Za-z0-9]{1,80}") || !t.currency().equals(fact.currency()) || fact.amountMinor()<1 || fact.amountMinor()>t.amountMinor())throw BillingErrors.state("Refund evidence does not match the captured payment.");
        RefundStatus state=switch(fact.status()){case "pending"->RefundStatus.PENDING;case "processed"->RefundStatus.PROCESSED;case "failed"->RefundStatus.FAILED;default->throw BillingErrors.state("Unknown refund state.");};
        var found=repository.providerRefund(org,fact.id());
        if(found.isEmpty() && fact.receipt()!=null){try {found=repository.refund(org,UUID.fromString(fact.receipt()));}catch(IllegalArgumentException ignored){/* External dashboard receipts need not be UUIDs. */}}
        if(expectedId!=null && (found.isEmpty() || !found.get().id().equals(expectedId)))throw BillingErrors.state("Provider refund receipt does not match this refund request.");
        Refund r;
        if(found.isPresent()){
            r=found.get();if(!r.transactionId().equals(t.id()) || r.amountMinor()!=fact.amountMinor() || r.providerRefundId()!=null && !r.providerRefundId().equals(fact.id()))throw BillingErrors.state("Provider refund belongs to a different request.");
            if(r.status()==RefundStatus.PROCESSED)state=RefundStatus.PROCESSED;
            if(r.status()==state && Objects.equals(r.providerRefundId(),fact.id()))return r;
            repository.refundState(r,fact.id(),state,actor,clock.instant());
        }else{
            r=new Refund(UUID.randomUUID(),org,t.id(),fact.id(),fact.amountMinor(),state,"Refund initiated in Razorpay","external:"+fact.id(),fingerprint.of(fact.id()),clock.instant(),0);repository.insert(r,actor);
        }
        long refunded=Math.max(t.refundedMinor(),repository.processedRefunds(org,t.id()));
        if(refunded>t.amountMinor())throw BillingErrors.state("Refund evidence exceeds the original payment.");
        if(refunded!=t.refundedMinor())repository.transactionState(t,refunded==t.amountMinor()?TransactionStatus.REFUNDED:TransactionStatus.PARTIALLY_REFUNDED,refunded,actor,clock.instant());
        evidence.recalculate(i,actor,"REFUND_EVIDENCE_RECORDED","PROVIDER");return repository.refund(org,r.id()).orElseThrow();
    }
    public record Prepared(Refund refund,Transaction transaction,boolean created){}
}
