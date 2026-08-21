package com.brainserve.onboarding.payments.infrastructure.persistence;
import com.brainserve.onboarding.payments.domain.model.*;import java.util.*;import org.springframework.data.jpa.repository.*;import org.springframework.data.repository.query.Param;
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction,UUID>{
 List<PaymentTransaction> findAllByOrganizationIdAndInvoiceIdOrderByOccurredAtAscIdAsc(UUID org,UUID invoiceId);
 boolean existsByProviderAndProviderEventId(String provider,String eventId);
 @Query("select coalesce(sum(t.amountMinor),0) from PaymentTransaction t where t.organizationId=:org and t.invoiceId=:invoice and t.transactionType in :types") long sumByTypes(@Param("org")UUID org,@Param("invoice")UUID invoice,@Param("types")Collection<PaymentTransactionType> types);
 @Query("select coalesce(sum(t.amountMinor),0) from PaymentTransaction t where t.organizationId=:org and t.paymentId=:payment and t.transactionType in :types") long sumPaymentByTypes(@Param("org")UUID org,@Param("payment")UUID payment,@Param("types")Collection<PaymentTransactionType> types);
}
