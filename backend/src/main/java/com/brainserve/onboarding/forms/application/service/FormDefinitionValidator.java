package com.brainserve.onboarding.forms.application.service;

import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.forms.api.request.FormFieldDraftRequest;
import com.brainserve.onboarding.forms.domain.model.FormField;
import com.brainserve.onboarding.forms.domain.model.FormFieldType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Validates immutable form-version definitions before persistence/publication. */
@Service
public class FormDefinitionValidator {
    private static final int MAX_FIELDS = 200;
    private static final int MAX_OPTIONS = 100;
    private static final int MAX_CONFIGURATION_BYTES = 24_000;

    private final FormConditionEngine conditions;
    private final ObjectMapper objectMapper;

    public FormDefinitionValidator(FormConditionEngine conditions, ObjectMapper objectMapper) {
        this.conditions = conditions;
        this.objectMapper = objectMapper;
    }

    public List<NormalizedField> normalize(List<FormFieldDraftRequest> input) {
        if (input == null || input.isEmpty()) throw invalid("A form version must contain at least one field.");
        if (input.size() > MAX_FIELDS) throw invalid("A form version cannot contain more than " + MAX_FIELDS + " fields.");
        Set<String> keys = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        Set<String> earlierKeys = new LinkedHashSet<>();
        List<NormalizedField> result = new ArrayList<>();
        for (FormFieldDraftRequest field : input.stream().sorted(java.util.Comparator.comparingInt(FormFieldDraftRequest::displayOrder)).toList()) {
            String key;
            try { key = FormField.normalizeKey(field.fieldKey()); }
            catch (IllegalArgumentException ex) { throw invalid(ex.getMessage()); }
            if (!keys.add(key)) throw invalid("Field keys must be unique within a form version: " + key);
            if (!orders.add(field.displayOrder())) throw invalid("Field display order must be unique within a form version.");
            JsonNode condition = conditions.normalizeAndValidate(field.conditionExpression(), Set.copyOf(earlierKeys));
            JsonNode config = normalizeConfiguration(field.fieldType(), field.configuration());
            result.add(new NormalizedField(key, field.label(), field.helpText(), field.fieldType(), field.displayOrder(),
                    field.required(), condition, config));
            earlierKeys.add(key);
        }
        return result;
    }

    public void validatePersisted(List<FormField> fields) {
        Set<String> earlierKeys = new LinkedHashSet<>();
        Set<String> keys = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        if (fields.isEmpty()) throw invalid("A form version must contain at least one field.");
        for (FormField field : fields.stream().sorted(java.util.Comparator.comparingInt(FormField::getDisplayOrder)).toList()) {
            if (!keys.add(field.getFieldKey()) || !orders.add(field.getDisplayOrder())) throw invalid("Stored form definition contains duplicate keys or display order.");
            conditions.normalizeAndValidate(field.getConditionExpression(), Set.copyOf(earlierKeys));
            normalizeConfiguration(field.getFieldType(), field.getConfigurationJson());
            earlierKeys.add(field.getFieldKey());
        }
    }

    private JsonNode normalizeConfiguration(FormFieldType type, JsonNode configuration) {
        JsonNode config = configuration;
        if (config == null || config.isNull()) config = objectMapper.createObjectNode();
        if (!config.isObject()) throw invalid("Field configuration must be a JSON object.");
        try {
            if (objectMapper.writeValueAsBytes(config).length > MAX_CONFIGURATION_BYTES) throw invalid("Field configuration is too large.");
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw invalid("Field configuration is invalid JSON.");
        }
        ObjectNode normalized = ((ObjectNode) config).deepCopy();
        if (Set.of(FormFieldType.DROPDOWN, FormFieldType.RADIO, FormFieldType.MULTI_SELECT).contains(type)) {
            JsonNode options = normalized.get("options");
            if (options == null || !options.isArray() || options.isEmpty() || options.size() > MAX_OPTIONS) {
                throw invalid(type + " fields require 1-" + MAX_OPTIONS + " options.");
            }
            Set<String> seen = new HashSet<>();
            for (JsonNode option : options) {
                if (!option.isTextual() || option.asText().isBlank() || option.asText().length() > 200) {
                    throw invalid("Choice options must be non-empty text up to 200 characters.");
                }
                if (!seen.add(option.asText().trim().toUpperCase(Locale.ROOT))) throw invalid("Choice options must be unique.");
            }
        } else if (normalized.has("options")) {
            throw invalid("Options are supported only for dropdown, radio, and multi-select fields.");
        }
        Integer minLength = integer(normalized, "minLength", 0, 20_000);
        Integer maxLength = integer(normalized, "maxLength", 1, 20_000);
        if (minLength != null && maxLength != null && minLength > maxLength) throw invalid("minLength cannot exceed maxLength.");
        if (normalized.has("pattern")) {
            if (!Set.of(FormFieldType.TEXT, FormFieldType.TEXTAREA).contains(type)) throw invalid("Pattern validation is supported only for text fields.");
            JsonNode pattern = normalized.get("pattern");
            if (!pattern.isTextual() || pattern.asText().length() > 300) throw invalid("Validation pattern is invalid.");
            try { java.util.regex.Pattern.compile(pattern.asText()); }
            catch (java.util.regex.PatternSyntaxException ex) { throw invalid("Validation pattern is not a valid regular expression."); }
        }
        if (normalized.has("min") || normalized.has("max")) {
            if (type != FormFieldType.NUMBER) throw invalid("Numeric min/max constraints are supported only for number fields.");
            if (normalized.has("min") && !normalized.get("min").isNumber()) throw invalid("min must be numeric.");
            if (normalized.has("max") && !normalized.get("max").isNumber()) throw invalid("max must be numeric.");
            if (normalized.has("min") && normalized.has("max")
                    && normalized.get("min").decimalValue().compareTo(normalized.get("max").decimalValue()) > 0) {
                throw invalid("min cannot exceed max.");
            }
        }
        if (type == FormFieldType.FILE) {
            if (normalized.has("maxFileSizeBytes")) {
                JsonNode maxFile = normalized.get("maxFileSizeBytes");
                long max = maxFile.canConvertToLong() ? maxFile.longValue() : -1;
                if (!maxFile.isIntegralNumber() || max <= 0 || max > 1_073_741_824L) {
                    throw invalid("maxFileSizeBytes must be an integer between 1 byte and 1 GiB.");
                }
            }
            if (normalized.has("allowedMimeTypes")) {
                JsonNode allowed = normalized.get("allowedMimeTypes");
                if (!allowed.isArray() || allowed.isEmpty() || allowed.size() > 40) {
                    throw invalid("allowedMimeTypes must contain 1-40 MIME types.");
                }
                Set<String> seen = new HashSet<>();
                for (JsonNode item : allowed) {
                    if (!item.isTextual()) throw invalid("allowedMimeTypes must contain text MIME values.");
                    String mime = item.asText().trim().toLowerCase(Locale.ROOT);
                    if (!mime.matches("[a-z0-9!#$&^_.+*-]+/[a-z0-9!#$&^_.+*-]+")) throw invalid("allowedMimeTypes contains an invalid MIME type.");
                    if (Set.of("text/html", "image/svg+xml", "application/xhtml+xml").contains(mime)) {
                        throw invalid("Active web-content MIME types are not permitted for file questions.");
                    }
                    if (!seen.add(mime)) throw invalid("allowedMimeTypes must be unique.");
                }
            }
        } else if (normalized.has("maxFileSizeBytes") || normalized.has("allowedMimeTypes")) {
            throw invalid("File-size and MIME constraints are supported only for file fields.");
        }
        return normalized;
    }

    private static Integer integer(ObjectNode node, String name, int min, int max) {
        if (!node.has(name)) return null;
        JsonNode value = node.get(name);
        if (!value.isInt()) throw invalid(name + " must be an integer.");
        int number = value.intValue();
        if (number < min || number > max) throw invalid(name + " is outside the supported range.");
        return number;
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "FORM_DEFINITION_INVALID", message);
    }

    public record NormalizedField(String fieldKey, String label, String helpText, FormFieldType fieldType,
                                  int displayOrder, boolean required, JsonNode conditionExpression, JsonNode configuration) {}
}
