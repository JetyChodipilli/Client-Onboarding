package com.brainserve.clientonboarding.payments.application;

import static com.brainserve.clientonboarding.payments.domain.model.PaymentModels.*;
import com.brainserve.clientonboarding.billing.application.*;
import com.brainserve.clientonboarding.common.domain.model.PageSlice;
import com.brainserve.clientonboarding.common.security.TenantPrincipal;
import com.brainserve.clientonboarding.payments.infrastructure.persistence.PaymentRepository;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class RefundService {
    private final RefundCommands commands;private final PaymentProvider provider;private final PaymentRepository repository;private final BillingLedger ledger;
    public RefundService(RefundCommands commands,PaymentProvider provider,PaymentRepository repository,BillingLedger ledger){this.commands=commands;this.provider=provider;this.repository=repository;this.ledger=ledger;}
    @PreAuthorize("hasAuthority('PAYMENT_REFUND')")
    public Refund create(TenantPrincipal p,UUID transaction,long amount,String reason,String key){
        var prepared=commands.prepare(p,transaction,amount,reason,key);var r=prepared.refund();
        if(!prepared.created() || prepared.transaction().provider().equals("MANUAL"))return r;
        try {return commands.apply(p.organizationId(),provider.createRefund(p.organizationId(),prepared.transaction().providerPaymentId(),r.id(),r.amountMinor()),r.id(),p.userId());}
        catch(ProviderFailure e){commands.failure(p.organizationId(),r.id(),e.definite(),p.userId());throw e;}
    }
    @PreAuthorize("hasAuthority('PAYMENT_REFUND')")
    public Refund reconcile(TenantPrincipal p,UUID refund,String providerId){var r=repository.refund(p.organizationId(),refund).orElseThrow(BillingErrors::missing);String id=r.providerRefundId()!=null?r.providerRefundId():providerId;if(id==null)throw BillingErrors.invalid("Provide the provider refund ID matching this request's receipt.");return commands.apply(p.organizationId(),provider.refund(p.organizationId(),id),r.id(),p.userId());}
    @PreAuthorize("hasAuthority('INVOICE_READ')")
    public PageSlice<Refund> list(TenantPrincipal p,UUID transaction,int page,int size){BillingErrors.page(page,size);var t=repository.transaction(p.organizationId(),transaction).orElseThrow(BillingErrors::missing);ledger.access(p,t.invoiceId(),false);return repository.refunds(p.organizationId(),transaction,page,size);}
}
