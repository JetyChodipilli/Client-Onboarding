package com.brainserve.onboarding.workflow.application.service;

import com.brainserve.onboarding.common.error.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Validated, non-executable condition DSL. Phase 3 deliberately supports only fields available
 * before later billing/forms/access modules exist. New fields/operators must be added explicitly.
 */
@Service
public class WorkflowConditionEngine {
    private static final int MAX_DEPTH = 6;
    private static final int MAX_NODES = 40;
    private static final int MAX_VALUES = 30;
    private static final Set<String> FIELDS = Set.of(
            "service.code", "service.id", "client.status", "client.id", "project.status", "project.id");
    private static final Set<String> LEAF_OPS = Set.of("EQ", "NEQ", "IN", "NOT_IN");
    private final ObjectMapper objectMapper;

    public WorkflowConditionEngine(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    public JsonNode normalizeAndValidate(JsonNode expression) {
        JsonNode value = expression;
        if (value == null || value.isNull() || (value.isObject() && value.size() == 0)) {
            ObjectNode always = objectMapper.createObjectNode();
            always.put("op", "ALWAYS");
            value = always;
        }
        Counter counter = new Counter();
        validate(value, 0, counter);
        return value.deepCopy();
    }

    public boolean evaluate(JsonNode expression, ConditionContext context) {
        JsonNode value = normalizeAndValidate(expression);
        return evaluateValidated(value, context);
    }

    private void validate(JsonNode node, int depth, Counter counter) {
        if (depth > MAX_DEPTH || ++counter.nodes > MAX_NODES) throw invalid("Condition is too complex");
        if (!node.isObject()) throw invalid("Condition must be a JSON object");
        String op = text(node, "op").toUpperCase(Locale.ROOT);
        switch (op) {
            case "ALWAYS" -> requireOnly(node, Set.of("op"));
            case "ALL", "ANY" -> {
                requireOnly(node, Set.of("op", "conditions"));
                JsonNode conditions = node.get("conditions");
                if (conditions == null || !conditions.isArray() || conditions.size() == 0 || conditions.size() > MAX_VALUES) {
                    throw invalid(op + " requires 1-" + MAX_VALUES + " conditions");
                }
                conditions.forEach(child -> validate(child, depth + 1, counter));
            }
            case "NOT" -> {
                requireOnly(node, Set.of("op", "condition"));
                JsonNode child = node.get("condition");
                if (child == null) throw invalid("NOT requires condition");
                validate(child, depth + 1, counter);
            }
            default -> {
                if (!LEAF_OPS.contains(op)) throw invalid("Unsupported condition operator: " + op);
                String field = text(node, "field");
                if (!FIELDS.contains(field)) throw invalid("Unsupported condition field: " + field);
                if (op.equals("EQ") || op.equals("NEQ")) {
                    requireOnly(node, Set.of("op", "field", "value"));
                    scalar(node.get("value"), "value");
                } else {
                    requireOnly(node, Set.of("op", "field", "values"));
                    JsonNode values = node.get("values");
                    if (values == null || !values.isArray() || values.size() == 0 || values.size() > MAX_VALUES) {
                        throw invalid(op + " requires 1-" + MAX_VALUES + " values");
                    }
                    values.forEach(value -> scalar(value, "values"));
                }
            }
        }
    }

    private boolean evaluateValidated(JsonNode node, ConditionContext context) {
        String op = node.get("op").asText().toUpperCase(Locale.ROOT);
        return switch (op) {
            case "ALWAYS" -> true;
            case "ALL" -> {
                boolean result = true;
                for (JsonNode child : node.get("conditions")) result &= evaluateValidated(child, context);
                yield result;
            }
            case "ANY" -> {
                boolean result = false;
                for (JsonNode child : node.get("conditions")) result |= evaluateValidated(child, context);
                yield result;
            }
            case "NOT" -> !evaluateValidated(node.get("condition"), context);
            case "EQ" -> normalized(context.fields().get(node.get("field").asText())).equals(normalized(node.get("value").asText()));
            case "NEQ" -> !normalized(context.fields().get(node.get("field").asText())).equals(normalized(node.get("value").asText()));
            case "IN" -> contains(node.get("values"), context.fields().get(node.get("field").asText()));
            case "NOT_IN" -> !contains(node.get("values"), context.fields().get(node.get("field").asText()));
            default -> false;
        };
    }

    private static boolean contains(JsonNode values, String actual) {
        String wanted = normalized(actual);
        for (JsonNode value : values) if (normalized(value.asText()).equals(wanted)) return true;
        return false;
    }

    private static String normalized(String value) { return value == null ? "" : value.trim().toUpperCase(Locale.ROOT); }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank() || value.asText().length() > 200) {
            throw invalid("Condition field '" + field + "' is invalid");
        }
        return value.asText();
    }

    private static void scalar(JsonNode value, String field) {
        if (value == null || !(value.isTextual() || value.isNumber() || value.isBoolean()) || value.asText().length() > 200) {
            throw invalid("Condition '" + field + "' must contain bounded scalar values");
        }
    }

    private static void requireOnly(JsonNode node, Set<String> allowed) {
        Set<String> actual = new HashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        if (!allowed.containsAll(actual)) throw invalid("Condition contains unsupported properties");
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "WORKFLOW_CONDITION_INVALID", message);
    }

    private static final class Counter { private int nodes; }

    public record ConditionContext(Map<String, String> fields) {
        public ConditionContext { fields = Map.copyOf(fields); }
    }
}
