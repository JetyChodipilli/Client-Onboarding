package com.brainserve.clientonboarding.workflow.domain.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

public record WorkflowCondition(Field field, Operator operator, String value) {
    public enum Field { SERVICE_CODE, PROJECT_VALUE_MINOR, CLIENT_STATUS, CURRENCY_CODE }
    public enum Operator { EQUALS, NOT_EQUALS, GREATER_THAN, GREATER_THAN_OR_EQUAL, IN }

    public WorkflowCondition {
        if (field == null || operator == null || value == null || value.isBlank() || value.length() > 300) {
            throw invalid();
        }
        if (field == Field.PROJECT_VALUE_MINOR) {
            if (!java.util.Set.of(Operator.EQUALS, Operator.NOT_EQUALS, Operator.GREATER_THAN,
                    Operator.GREATER_THAN_OR_EQUAL).contains(operator)) throw invalid();
            try { Long.parseLong(value); } catch (NumberFormatException exception) { throw invalid(); }
        } else if (operator == Operator.GREATER_THAN || operator == Operator.GREATER_THAN_OR_EQUAL) {
            throw invalid();
        }
    }

    public boolean evaluate(Map<Field, String> context) {
        String actual = context.get(field);
        if (actual == null) return false;
        return switch (operator) {
            case EQUALS -> actual.equalsIgnoreCase(value);
            case NOT_EQUALS -> !actual.equalsIgnoreCase(value);
            case GREATER_THAN -> Long.parseLong(actual) > Long.parseLong(value);
            case GREATER_THAN_OR_EQUAL -> Long.parseLong(actual) >= Long.parseLong(value);
            case IN -> Arrays.stream(value.split(",")).map(String::trim)
                    .anyMatch(candidate -> actual.equalsIgnoreCase(candidate));
        };
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException(
                "Workflow conditions must use a supported field, operator, and value.");
    }
}
