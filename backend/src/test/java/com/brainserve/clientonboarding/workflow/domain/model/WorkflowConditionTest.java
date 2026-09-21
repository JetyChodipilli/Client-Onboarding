package com.brainserve.clientonboarding.workflow.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkflowConditionTest {

    @Test
    void evaluatesValidatedTextSetAndNumericConditions() {
        assertThat(new WorkflowCondition(WorkflowCondition.Field.SERVICE_CODE,
                WorkflowCondition.Operator.EQUALS, "meta_ads")
                .evaluate(Map.of(WorkflowCondition.Field.SERVICE_CODE, "META_ADS"))).isTrue();
        assertThat(new WorkflowCondition(WorkflowCondition.Field.CLIENT_STATUS,
                WorkflowCondition.Operator.IN, "PROSPECT, ACTIVE")
                .evaluate(Map.of(WorkflowCondition.Field.CLIENT_STATUS, "ACTIVE"))).isTrue();
        assertThat(new WorkflowCondition(WorkflowCondition.Field.PROJECT_VALUE_MINOR,
                WorkflowCondition.Operator.GREATER_THAN, "500000")
                .evaluate(Map.of(WorkflowCondition.Field.PROJECT_VALUE_MINOR, "500001"))).isTrue();
        assertThat(new WorkflowCondition(WorkflowCondition.Field.CURRENCY_CODE,
                WorkflowCondition.Operator.EQUALS, "USD").evaluate(Map.of())).isFalse();
    }

    @Test
    void rejectsUnsupportedOrMalformedExpressions() {
        assertThatThrownBy(() -> new WorkflowCondition(WorkflowCondition.Field.CURRENCY_CODE,
                WorkflowCondition.Operator.GREATER_THAN, "USD")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WorkflowCondition(WorkflowCondition.Field.PROJECT_VALUE_MINOR,
                WorkflowCondition.Operator.GREATER_THAN, "not-a-number"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
