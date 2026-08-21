package com.brainserve.onboarding.contracts.api.controller;

import com.brainserve.onboarding.common.api.ApiResponse;
import com.brainserve.onboarding.common.observability.RequestContext;
import com.brainserve.onboarding.contracts.application.service.ContractWebhookService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/webhooks/contracts")
public class ContractWebhookController {
    private final ContractWebhookService service;
    public ContractWebhookController(ContractWebhookService service){this.service=service;}

    @PostMapping("/{provider}")
    ApiResponse<Map<String,Object>> callback(@PathVariable String provider,@RequestBody byte[] body,@RequestHeader HttpHeaders headers){
        Map<String,String> flat=new LinkedHashMap<>();headers.forEach((name,values)->{if(values!=null&&!values.isEmpty())flat.put(name,values.getFirst());});
        var result=service.process(provider,body,flat);
        return ApiResponse.success(Map.of("duplicate",result.duplicate(),"status",result.status()),RequestContext.requestId());
    }
}
