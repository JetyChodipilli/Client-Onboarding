package com.brainserve.onboarding.billing.infrastructure.persistence;
import com.brainserve.onboarding.billing.domain.model.InvoiceItem;import java.util.*;import org.springframework.data.jpa.repository.JpaRepository;
public interface InvoiceItemRepository extends JpaRepository<InvoiceItem,UUID>{List<InvoiceItem> findAllByOrganizationIdAndInvoiceIdOrderByDisplayOrderAscIdAsc(UUID organizationId,UUID invoiceId);}
