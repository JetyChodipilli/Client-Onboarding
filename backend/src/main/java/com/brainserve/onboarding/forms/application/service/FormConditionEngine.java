package com.brainserve.onboarding.forms.application.service;

import com.brainserve.onboarding.common.error.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Small, deterministic condition DSL for questionnaire field visibility. It never executes user supplied code.
 * Conditions may only reference explicitly supplied field keys and have bounded depth/size.
 */
@Service
public class FormConditionEngine {
    private static final int MAX_DEPTH = 6;
    private static final int MAX_NODES = 40;
    private static final int MAX_VALUES = 30;
    private static final Set<String> LEAF_OPS = Set.of(
            "EQ", "NEQ", "IN", "NOT_IN", "EXISTS", "NOT_EXISTS", "IS_TRUE", "IS_FALSE");

    private final ObjectMapper objectMapper;

    public FormConditionEngine(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode normalizeAndValidate(JsonNode expression, Set<String> allowedFieldKeys) {
        JsonNode value = expression;
        if (value == null || value.isNull() || (value.isObject() && value.size() == 0)) {
            ObjectNode always = objectMapper.createObjectNode();
            always.put("op", "ALWAYS");
            value = always;
        }
        Counter counter = new Counter();
        validate(value, allowedFieldKeys, 0, counter);
        return value.deepCopy();
    }

    public boolean evaluate(JsonNode expression, Map<String, JsonNode> answers) {
        JsonNode value = normalizeAndValidate(expression, answers.keySet());
        return evaluateValidated(value, answers);
    }

    public boolean evaluateValidated(JsonNode expression, Map<String, JsonNode> answers) {
        String op = expression.path("op").asText().toUpperCase(Locale.ROOT);
        return switch (op) {
            case "ALWAYS" -> true;
            case "ALL" -> {
                boolean result = true;
                for (JsonNode child : expression.path("conditions")) result &= evaluateValidated(child, answers);
                yield result;
            }
            case "ANY" -> {
                boolean result = false;
                for (JsonNode child : expression.path("conditions")) result |= evaluateValidated(child, answers);
                yield result;
            }
            case "NOT" -> !evaluateValidated(expression.path("condition"), answers);
            case "EXISTS" -> hasValue(answers.get(normalizeKey(expression.path("field").asText())));
            case "NOT_EXISTS" -> !hasValue(answers.get(normalizeKey(expression.path("field").asText())));
            case "IS_TRUE" -> truthy(answers.get(normalizeKey(expression.path("field").asText())));
            case "IS_FALSE" -> !truthy(answers.get(normalizeKey(expression.path("field").asText())));
            case "EQ" -> equivalent(answers.get(normalizeKey(expression.path("field").asText())), expression.get("value"));
            case "NEQ" -> !equivalent(answers.get(normalizeKey(expression.path("field").asText())), expression.get("value"));
            case "IN" -> anyEquivalent(expression.path("values"), answers.get(normalizeKey(expression.path("field").asText())));
            case "NOT_IN" -> !anyEquivalent(expression.path("values"), answers.get(normalizeKey(expression.path("field").asText())));
            default -> false;
        };
    }

    private void validate(JsonNode node, Set<String> allowedFieldKeys, int depth, Counter counter) {
        if (depth > MAX_DEPTH || ++counter.nodes > MAX_NODES) throw invalid("Condition is too complex.");
        if (!node.isObject()) throw invalid("Condition must be a JSON object.");
        String op = requiredText(node, "op").toUpperCase(Locale.ROOT);
        switch (op) {
            case "ALWAYS" -> requireOnly(node, Set.of("op"));
            case "ALL", "ANY" -> {
                requireOnly(node, Set.of("op", "conditions"));
                JsonNode children = node.get("conditions");
                if (children == null || !children.isArray() || children.size() == 0 || children.size() > MAX_VALUES) {
                    throw invalid(op + " requires 1-" + MAX_VALUES + " child conditions.");
                }
                children.forEach(child -> validate(child, allowedFieldKeys, depth + 1, counter));
            }
            case "NOT" -> {
                requireOnly(node, Set.of("op", "condition"));
                JsonNode child = node.get("condition");
                if (child == null) throw invalid("NOT requires a child condition.");
                validate(child, allowedFieldKeys, depth + 1, counter);
            }
            default -> {
                if (!LEAF_OPS.contains(op)) throw invalid("Unsupported form condition operator: " + op);
                String key = normalizeKey(requiredText(node, "field"));
                if (!allowedFieldKeys.contains(key)) {
                    throw invalid("Condition references an unavailable field: " + key);
                }
                if (Set.of("EXISTS", "NOT_EXISTS", "IS_TRUE", "IS_FALSE").contains(op)) {
                    requireOnly(node, Set.of("op", "field"));
                } else if (Set.of("EQ", "NEQ").contains(op)) {
                    requireOnly(node, Set.of("op", "field", "value"));
                    scalar(node.get("value"));
                } else {
                    requireOnly(node, Set.of("op", "field", "values"));
                    JsonNode values = node.get("values");
                    if (values == null || !values.isArray() || values.size() == 0 || values.size() > MAX_VALUES) {
                        throw invalid(op + " requires 1-" + MAX_VALUES + " scalar values.");
                    }
                    values.forEach(FormConditionEngine::scalar);
                }
            }
        }
    }

    private static boolean anyEquivalent(JsonNode allowed, JsonNode actual) {
        if (actual == null || actual.isNull()) return false;
        if (actual.isArray()) {
            for (JsonNode item : actual) if (anyEquivalent(allowed, item)) return true;
            return false;
        }
        for (JsonNode value : allowed) if (equivalent(actual, value)) return true;
        return false;
    }

    private static boolean equivalent(JsonNode left, JsonNode right) {
        if (left == null || left.isNull() || right == null || right.isNull()) return false;
        if (left.isArray()) {
            for (JsonNode item : left) if (equivalent(item, right)) return true;
            return false;
        }
        if (left.isNumber() && right.isNumber()) {
            return left.decimalValue().compareTo(right.decimalValue()) == 0;
        }
        if (left.isBoolean() && right.isBoolean()) return left.booleanValue() == right.booleanValue();
        return normalizedScalar(left).equals(normalizedScalar(right));
    }

    private static boolean truthy(JsonNode node) {
        if (node == null || node.isNull()) return false;
        if (node.isBoolean()) return node.booleanValue();
        if (node.isTextual()) return "TRUE".equals(node.asText().trim().toUpperCase(Locale.ROOT));
        return false;
    }

    public static boolean hasValue(JsonNode value) {
        if (value == null || value.isNull()) return false;
        if (value.isTextual()) return !value.asText().trim().isEmpty();
        if (value.isArray()) return value.size() > 0;
        return true;
    }

    private static String normalizedScalar(JsonNode node) {
        if (node.isNumber()) return new BigDecimal(node.asText()).stripTrailingZeros().toPlainString();
        return node.asText().trim().toUpperCase(Locale.ROOT);
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank() || value.asText().length() > 200) {
            throw invalid("Condition property '" + field + "' is invalid.");
        }
        return value.asText();
    }

    private static String normalizeKey(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static void scalar(JsonNode node) {
        if (node == null || node.isNull() || !(node.isTextual() || node.isBoolean() || node.isNumber()) || node.asText().length() > 500) {
            throw invalid("Condition values must be bounded scalar values.");
        }
    }

    private static void requireOnly(JsonNode node, Set<String> allowed) {
        Set<String> actual = new HashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        if (!allowed.containsAll(actual)) throw invalid("Condition contains unsupported properties.");
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "FORM_CONDITION_INVALID", message);
    }

    private static final class Counter { private int nodes; }
}
