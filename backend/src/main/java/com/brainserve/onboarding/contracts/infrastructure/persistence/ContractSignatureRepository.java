package com.brainserve.onboarding.contracts.infrastructure.persistence;
import com.brainserve.onboarding.contracts.domain.model.ContractSignature;import java.util.*;import org.springframework.data.jpa.repository.JpaRepository;
public interface ContractSignatureRepository extends JpaRepository<ContractSignature,UUID>{boolean existsByProviderAndProviderEventId(String provider,String eventId);List<ContractSignature> findAllByOrganizationIdAndContractIdOrderBySignedAtAscIdAsc(UUID org,UUID contractId);}
