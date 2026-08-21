package com.brainserve.onboarding.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.workflow.application.service.WorkflowConditionEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkflowConditionEngineTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final WorkflowConditionEngine engine = new WorkflowConditionEngine(mapper);

    @Test
    void evaluatesValidatedNestedConditions() throws Exception {
        var expression = mapper.readTree("""
                {"op":"ALL","conditions":[
                  {"op":"EQ","field":"service.code","value":"META_ADS"},
                  {"op":"ANY","conditions":[
                    {"op":"EQ","field":"client.status","value":"ACTIVE"},
                    {"op":"EQ","field":"client.status","value":"PROSPECT"}
                  ]}
                ]}
                """);
        var context = new WorkflowConditionEngine.ConditionContext(Map.of("service.code", "meta_ads", "client.status", "ACTIVE"));
        assertThat(engine.evaluate(expression, context)).isTrue();
    }

    @Test
    void rejectsExecutableOrUnknownDslProperties() throws Exception {
        var expression = mapper.readTree("{\"op\":\"EQ\",\"field\":\"java.lang.Runtime\",\"value\":\"exec\"}");
        assertThatThrownBy(() -> engine.normalizeAndValidate(expression))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Unsupported condition field");
    }
}
