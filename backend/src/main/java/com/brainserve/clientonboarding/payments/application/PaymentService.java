package com.brainserve.clientonboarding.payments.application;

import static com.brainserve.clientonboarding.payments.domain.model.PaymentModels.*;
import com.brainserve.clientonboarding.billing.application.*;
import com.brainserve.clientonboarding.common.domain.model.PageSlice;
import com.brainserve.clientonboarding.common.security.TenantPrincipal;
import com.brainserve.clientonboarding.payments.infrastructure.persistence.PaymentRepository;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PaymentService {
    private final PaymentProvider provider;private final PaymentCommands commands;private final PaymentEvidenceService evidence;private final PaymentRepository repository;private final BillingLedger ledger;private final TransactionTemplate tx;
    public PaymentService(PaymentProvider provider,PaymentCommands commands,PaymentEvidenceService evidence,PaymentRepository repository,BillingLedger ledger,org.springframework.transaction.PlatformTransactionManager manager){this.provider=provider;this.commands=commands;this.evidence=evidence;this.repository=repository;this.ledger=ledger;tx=new TransactionTemplate(manager);}
    @PreAuthorize("hasAuthority('CLIENT_PORTAL_STEP_UPDATE')")
    public Checkout checkout(TenantPrincipal p,UUID invoice,long amount,String key){
        ledger.access(p,invoice,true);if(!provider.available(p.organizationId()))throw BillingErrors.state("Online payment is unavailable. Contact your project team.");
        var prepared=commands.prepare(p,invoice,amount,key);var s=prepared.session();
        if(prepared.created()){
            try {s=evidence.attach(p.organizationId(),s.id(),provider.createOrder(p.organizationId(),s.id(),s.amountMinor()),p.userId());}
            catch(ProviderFailure e){evidence.orderFailure(p.organizationId(),s.id(),e.definite(),p.userId());throw e;}
        }
        ledger.access(p,invoice,true);ledger.accepting(ledger.require(p.organizationId(),invoice));return checkout(s);
    }
    public History history(TenantPrincipal p,UUID invoice,boolean client,int page,int size){ledger.access(p,invoice,client);BillingErrors.page(page,size);var s=repository.open(p.organizationId(),invoice);return new History(repository.transactions(p.organizationId(),invoice,page,size),s.map(this::checkout).orElse(null),provider.available(p.organizationId()));}
    @PreAuthorize("hasAuthority('CLIENT_PORTAL_STEP_UPDATE')")
    public void confirm(TenantPrincipal p,UUID session,String paymentId,String signature){
        var s=repository.session(p.organizationId(),session).orElseThrow(BillingErrors::missing);ledger.access(p,s.invoiceId(),true);
        if(s.providerOrderId()==null || !provider.verifyCheckout(p.organizationId(),s.providerOrderId(),paymentId,signature))throw BillingErrors.invalid("Checkout signature could not be verified.");
        var payment=provider.payment(p.organizationId(),paymentId);
        if(!s.providerOrderId().equals(payment.orderId()))throw BillingErrors.state("Payment does not belong to this checkout.");
        evidence.apply(p.organizationId(),payment,p.userId(),"CHECKOUT_VERIFIED");
    }
    @PreAuthorize("hasAuthority('PAYMENT_RECONCILE')")
    public void reconcile(TenantPrincipal p,UUID session,String orderId){
        var s=repository.session(p.organizationId(),session).orElseThrow(BillingErrors::missing);
        if(s.providerOrderId()==null){if(orderId==null)throw BillingErrors.invalid("Provide the Razorpay order ID whose receipt matches this payment attempt.");s=evidence.attach(p.organizationId(),s.id(),provider.order(p.organizationId(),orderId),p.userId());}
        else if(orderId!=null && !orderId.equals(s.providerOrderId()))throw BillingErrors.state("The order ID does not match this payment attempt.");
        for(var payment:provider.orderPayments(p.organizationId(),s.providerOrderId())){
            if(!s.providerOrderId().equals(payment.orderId()))throw BillingErrors.state("Provider returned a payment for a different order.");
            evidence.apply(p.organizationId(),payment,p.userId(),"RECONCILIATION");
        }
        UUID invoice=s.invoiceId();tx.executeWithoutResult(status->ledger.sync(ledger.lock(p.organizationId(),invoice),p.userId()));
    }
    @PreAuthorize("hasAuthority('PAYMENT_RECONCILE')")
    public void sync(TenantPrincipal p,UUID invoice){tx.executeWithoutResult(status->ledger.sync(ledger.lock(p.organizationId(),invoice),p.userId()));}
    private Checkout checkout(Session s){return new Checkout(s.id(),s.invoiceId(),s.status(),s.status()==SessionStatus.READY?provider.publicKey(s.organizationId()):null,s.providerOrderId(),s.amountMinor(),s.currency(),s.id().toString(),s.version());}
    public record Checkout(UUID id,UUID invoiceId,SessionStatus status,String keyId,String orderId,long amountMinor,String currency,String receipt,long version){}
    public record History(PageSlice<Transaction> transactions,Checkout pendingCheckout,boolean providerAvailable){}
}
