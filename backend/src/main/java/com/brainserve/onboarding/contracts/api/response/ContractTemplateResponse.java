package com.brainserve.onboarding.contracts.api.response;
import com.brainserve.onboarding.contracts.domain.model.ContractTemplateStatus;import java.time.Instant;import java.util.*;
public record ContractTemplateResponse(UUID id,String name,String description,ContractTemplateStatus status,Instant createdAt,Instant updatedAt,long version,List<ContractTemplateVersionResponse> versions){public ContractTemplateResponse{versions=List.copyOf(versions);}}
