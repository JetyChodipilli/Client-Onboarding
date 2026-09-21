package com.brainserve.clientonboarding.audit.api;

import com.brainserve.clientonboarding.audit.domain.model.AuditEntry;
import com.brainserve.clientonboarding.audit.domain.repository.AuditRepository;
import com.brainserve.clientonboarding.common.security.CurrentPrincipal;
import com.brainserve.clientonboarding.common.api.ApiSuccess;
import com.brainserve.clientonboarding.common.observability.RequestIds;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/audit-logs")
public class AuditController {
    private final AuditRepository repository;

    public AuditController(AuditRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    ApiSuccess<List<AuditEntry>> list(Authentication authentication,
                                      @RequestParam(defaultValue = "0") @Min(0) int page,
                                      @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        var principal = CurrentPrincipal.require(authentication);
        long total = repository.count(principal.organizationId());
        return new ApiSuccess<>(true, repository.findPage(principal.organizationId(), size, page * size),
                Map.of("page", page, "size", size, "total", total), RequestIds.currentRequestId());
    }
}
