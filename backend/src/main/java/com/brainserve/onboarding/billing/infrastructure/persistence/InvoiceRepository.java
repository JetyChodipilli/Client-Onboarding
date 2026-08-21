package com.brainserve.onboarding.billing.infrastructure.persistence;
import com.brainserve.onboarding.billing.domain.model.Invoice;import com.brainserve.onboarding.billing.domain.model.InvoiceStatus;import jakarta.persistence.LockModeType;import java.util.*;import org.springframework.data.domain.*;import org.springframework.data.jpa.repository.*;import org.springframework.data.repository.query.Param;
public interface InvoiceRepository extends JpaRepository<Invoice,UUID>{
 Optional<Invoice> findByOrganizationIdAndId(UUID organizationId,UUID id);
 Optional<Invoice> findByOrganizationIdAndCreationIdempotencyKey(UUID organizationId,String key);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select i from Invoice i where i.organizationId=:org and i.id=:id") Optional<Invoice> findForUpdate(@Param("org")UUID org,@Param("id")UUID id);
 Page<Invoice> findAllByOrganizationId(UUID organizationId,Pageable pageable);
 Page<Invoice> findAllByOrganizationIdAndStatus(UUID organizationId,InvoiceStatus status,Pageable pageable);
 Page<Invoice> findAllByOrganizationIdAndProjectId(UUID organizationId,UUID projectId,Pageable pageable);
 Page<Invoice> findAllByOrganizationIdAndProjectIdAndStatusIn(UUID organizationId,UUID projectId,Collection<InvoiceStatus> statuses,Pageable pageable);
 Page<Invoice> findAllByOrganizationIdAndProjectIdAndStatus(UUID organizationId,UUID projectId,InvoiceStatus status,Pageable pageable);
 List<Invoice> findAllByOrganizationIdAndProjectIdOrderByCreatedAtDescIdDesc(UUID organizationId,UUID projectId);
 List<Invoice> findAllByOrganizationIdAndStepInstanceIdOrderByCreatedAtDescIdDesc(UUID organizationId,UUID stepId);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select i from Invoice i where i.status in :statuses and i.dueAt is not null and i.dueAt<:now order by i.dueAt asc, i.id asc") List<Invoice> findOverdueForUpdate(@Param("statuses")Collection<InvoiceStatus> statuses,@Param("now")java.time.Instant now,Pageable pageable);
}
