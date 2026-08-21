package com.brainserve.onboarding.payments.api.controller;

import com.brainserve.onboarding.common.api.ApiResponse;
import com.brainserve.onboarding.common.observability.RequestContext;
import com.brainserve.onboarding.payments.application.service.PaymentWebhookService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/webhooks/payments")
public class PaymentWebhookController {
    private final PaymentWebhookService service;

    public PaymentWebhookController(PaymentWebhookService service) { this.service = service; }

    @PostMapping("/{provider}")
    ApiResponse<Map<String, Object>> receive(@PathVariable String provider,
                                             @RequestBody byte[] body,
                                             @RequestHeader HttpHeaders headers) {
        Map<String, String> flattened = new LinkedHashMap<>();
        headers.forEach((key, values) -> {
            if (!values.isEmpty()) flattened.put(key, values.getFirst());
        });
        var result = service.process(provider, body, flattened);
        return ApiResponse.success(Map.of("accepted", true, "duplicate", result.duplicate(), "status", result.status()), RequestContext.requestId());
    }
}
