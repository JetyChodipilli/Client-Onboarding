package com.brainserve.clientonboarding.audit.api;

import com.brainserve.clientonboarding.audit.domain.model.AuditEntry;
import com.brainserve.clientonboarding.audit.application.AuditService;
import com.brainserve.clientonboarding.common.security.CurrentPrincipal;
import com.brainserve.clientonboarding.common.api.ApiSuccess;
import com.brainserve.clientonboarding.common.observability.RequestIds;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
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
    private final AuditService service;

    public AuditController(AuditService service) {
        this.service = service;
    }

    @GetMapping
    ApiSuccess<List<AuditEntry>> list(Authentication authentication,
                                      @RequestParam(defaultValue = "0") @Min(0) int page,
                                      @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        var principal = CurrentPrincipal.require(authentication);
        var result = service.page(principal.organizationId(), page, size);
        return new ApiSuccess<>(true, result.items(), result.meta(), RequestIds.currentRequestId());
    }
}
