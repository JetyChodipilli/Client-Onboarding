package com.brainserve.onboarding.payments.application.service;

import com.brainserve.onboarding.billing.domain.model.Invoice;
import com.brainserve.onboarding.billing.domain.model.InvoiceStatus;
import com.brainserve.onboarding.billing.infrastructure.persistence.InvoiceRepository;
import com.brainserve.onboarding.common.event.OutboxService;
import java.time.Clock;
import java.util.EnumSet;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Small bounded maintenance job that marks sent/viewed invoices overdue without external I/O. */
@Service
public class InvoiceOverdueService {
    private final InvoiceRepository invoices;
    private final OutboxService outbox;
    private final Clock clock;

    public InvoiceOverdueService(InvoiceRepository invoices, OutboxService outbox, Clock clock) {
        this.invoices = invoices; this.outbox = outbox; this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.payments.overdue-scan-delay:PT5M}")
    @Transactional
    public void scan() {
        var candidates = invoices.findOverdueForUpdate(EnumSet.of(InvoiceStatus.SENT, InvoiceStatus.VIEWED), clock.instant(), PageRequest.of(0, 100));
        for (Invoice invoice : candidates) {
            InvoiceStatus before = invoice.getStatus();
            invoice.markOverdue(clock.instant());
            if (invoice.getStatus() != before) {
                invoices.save(invoice);
                outbox.record(invoice.getOrganizationId(), "INVOICE_OVERDUE", "INVOICE", invoice.getId(),
                        java.util.Map.of("invoiceId", invoice.getId(), "projectId", invoice.getProjectId()));
            }
        }
        invoices.flush();
    }
}
