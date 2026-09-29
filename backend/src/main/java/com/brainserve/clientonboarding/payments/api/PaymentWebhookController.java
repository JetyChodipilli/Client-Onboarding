package com.brainserve.clientonboarding.payments.api;

import com.brainserve.clientonboarding.payments.application.PaymentWebhookService;
import com.brainserve.clientonboarding.common.api.ApiSuccess;
import com.brainserve.clientonboarding.common.observability.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class PaymentWebhookController {
    private final PaymentWebhookService service;
    public PaymentWebhookController(PaymentWebhookService service){this.service=service;}
    @PostMapping("/api/v1/webhooks/payments/razorpay/{organizationId}")
    ApiSuccess<Map<String,String>> receive(@PathVariable UUID organizationId,@RequestHeader(value="X-Razorpay-Signature",required=false)String signature,@RequestHeader(value="X-Razorpay-Event-Id",required=false)String eventId,HttpServletRequest request)throws IOException {
        service.receive(organizationId,request.getInputStream().readNBytes(1048577),signature,eventId);
        return ApiSuccess.of(Map.of("message","Webhook acknowledged."),RequestIds.currentRequestId());
    }
}
