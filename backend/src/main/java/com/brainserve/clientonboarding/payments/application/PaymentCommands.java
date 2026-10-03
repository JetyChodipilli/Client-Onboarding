package com.brainserve.clientonboarding.payments.application;

import static com.brainserve.clientonboarding.payments.domain.model.PaymentModels.*;
import com.brainserve.clientonboarding.billing.application.*;
import com.brainserve.clientonboarding.billing.domain.model.BillingModels.Policy;
import com.brainserve.clientonboarding.common.security.TenantPrincipal;
import com.brainserve.clientonboarding.payments.infrastructure.persistence.PaymentRepository;
import java.time.Clock;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentCommands {
    private final PaymentRepository repository;private final BillingLedger ledger;private final BillingFingerprint fingerprint;private final PaymentEvidenceService evidence;private final Clock clock;
    public PaymentCommands(PaymentRepository repository,BillingLedger ledger,BillingFingerprint fingerprint,PaymentEvidenceService evidence,Clock clock){this.repository=repository;this.ledger=ledger;this.fingerprint=fingerprint;this.evidence=evidence;this.clock=clock;}
    @PreAuthorize("hasAuthority('CLIENT_PORTAL_STEP_UPDATE')") @Transactional
    public Prepared prepare(TenantPrincipal p,UUID invoice,long amount,String key){
        BillingErrors.key(key);ledger.access(p,invoice,true);ledger.lockCommands(p.organizationId());var i=ledger.lock(p.organizationId(),invoice);ledger.accepting(i);
        if(i.policy()==Policy.MANUAL || i.policy()==Policy.NO_PAYMENT_REQUIRED)throw BillingErrors.state("This invoice does not accept online payments.");
        String hash=fingerprint.of(List.of(invoice,amount));var duplicate=repository.sessionKey(p.organizationId(),key,hash);if(duplicate.isPresent())return new Prepared(duplicate.get(),false);
        var open=repository.open(p.organizationId(),invoice);if(open.isPresent()){if(open.get().amountMinor()!=amount)throw BillingErrors.state("An existing checkout reserves a different amount. Resume it or ask your team to reconcile it.");return new Prepared(open.get(),false);}
        if(amount<100 || amount>i.balanceMinor()-i.reservedMinor())throw BillingErrors.invalid("Payment must be at least INR 1.00 and no more than the available balance.");
        var s=new Session(UUID.randomUUID(),p.organizationId(),invoice,null,amount,"INR",SessionStatus.CREATING,key,hash,clock.instant(),0);repository.insert(s,p.userId());evidence.recalculate(i,p.userId(),"PAYMENT_ORDER_REQUESTED","API");return new Prepared(s,true);
    }
    @PreAuthorize("hasAuthority('PAYMENT_OVERRIDE')") @Transactional
    public Transaction manual(TenantPrincipal p,UUID invoice,long amount,String reference,String reason,String key){
        BillingErrors.key(key);reason=BillingErrors.reason(reason);if(reference==null || !reference.matches("[A-Za-z0-9._:/ -]{3,120}"))throw BillingErrors.invalid("Provide an external payment reference of 3 to 120 characters.");
        ledger.lockCommands(p.organizationId());var i=ledger.lock(p.organizationId(),invoice);String hash=fingerprint.of(List.of(invoice,amount,reference,reason));var duplicate=repository.manualKey(p.organizationId(),key,hash);if(duplicate.isPresent())return duplicate.get();
        ledger.accepting(i);if(amount<1 || amount>i.balanceMinor() || i.reservedMinor()!=0)throw BillingErrors.state("Manual payment exceeds the balance or an online checkout is still pending.");
        var t=new Transaction(UUID.randomUUID(),p.organizationId(),invoice,null,"MANUAL",null,TransactionStatus.CAPTURED,amount,0,"INR",reference,reason,clock.instant(),0);repository.insert(t,key,hash,p.userId());evidence.recalculate(i,p.userId(),"MANUAL_PAYMENT_RECORDED","MANUAL_OVERRIDE");return t;
    }
    public record Prepared(Session session,boolean created){}
}
