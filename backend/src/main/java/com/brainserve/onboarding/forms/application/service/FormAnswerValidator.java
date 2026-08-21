package com.brainserve.onboarding.forms.application.service;

import com.brainserve.onboarding.common.error.ApiException;
import com.brainserve.onboarding.forms.domain.model.FormField;
import com.brainserve.onboarding.forms.domain.model.FormFieldType;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class FormAnswerValidator {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final int MAX_TEXT = 20_000;

    private final FormConditionEngine conditions;

    public FormAnswerValidator(FormConditionEngine conditions) { this.conditions = conditions; }

    public void validateDraft(ListView fields, Map<String, JsonNode> answers) {
        validateKnown(fields, answers);
        for (FormField field : fields.values()) {
            JsonNode answer = answers.get(field.getFieldKey());
            if (FormConditionEngine.hasValue(answer)) validateValue(field, answer);
        }
    }

    public void validateSubmission(ListView fields, Map<String, JsonNode> answers) {
        validateKnown(fields, answers);
        for (FormField field : fields.values()) {
            boolean visible = conditions.evaluateValidated(field.getConditionExpression(), answers);
            if (!visible) continue;
            JsonNode answer = answers.get(field.getFieldKey());
            if (field.isRequired() && !FormConditionEngine.hasValue(answer)) {
                throw invalid(field.getFieldKey(), "This required field must be completed.");
            }
            if (FormConditionEngine.hasValue(answer)) validateValue(field, answer);
        }
    }

    private static void validateKnown(ListView fields, Map<String, JsonNode> answers) {
        if (answers.size() > 200) throw invalid(null, "Too many answers were supplied.");
        Set<String> known = fields.keys();
        for (String key : answers.keySet()) if (!known.contains(key)) throw invalid(key, "Unknown form field.");
    }

    private static void validateValue(FormField field, JsonNode value) {
        FormFieldType type = field.getFieldType();
        switch (type) {
            case TEXT, TEXTAREA -> validateText(field, value);
            case EMAIL -> {
                String text = text(field, value);
                if (text.length() > 320 || !EMAIL.matcher(text).matches()) throw invalid(field.getFieldKey(), "Enter a valid email address.");
            }
            case URL -> {
                String text = text(field, value);
                try {
                    URI uri = URI.create(text);
                    if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null) {
                        throw new IllegalArgumentException();
                    }
                } catch (IllegalArgumentException ex) { throw invalid(field.getFieldKey(), "Enter a valid HTTP or HTTPS URL."); }
            }
            case DATE -> {
                String text = text(field, value);
                try { LocalDate.parse(text); } catch (DateTimeParseException ex) { throw invalid(field.getFieldKey(), "Enter a valid ISO date."); }
            }
            case NUMBER -> {
                if (!value.isNumber()) throw invalid(field.getFieldKey(), "Enter a valid number.");
                BigDecimal number = value.decimalValue();
                JsonNode config = field.getConfigurationJson();
                if (config.has("min") && number.compareTo(config.get("min").decimalValue()) < 0) throw invalid(field.getFieldKey(), "Number is below the minimum.");
                if (config.has("max") && number.compareTo(config.get("max").decimalValue()) > 0) throw invalid(field.getFieldKey(), "Number exceeds the maximum.");
            }
            case BOOLEAN, CHECKBOX -> {
                if (!value.isBoolean()) throw invalid(field.getFieldKey(), "Enter a true/false value.");
            }
            case DROPDOWN, RADIO -> validateChoice(field, value, false);
            case MULTI_SELECT -> validateChoice(field, value, true);
            case FILE -> {
                if (!value.isTextual()) throw invalid(field.getFieldKey(), "Select a securely uploaded file.");
                try { java.util.UUID.fromString(value.asText().trim()); }
                catch (IllegalArgumentException ex) { throw invalid(field.getFieldKey(), "File reference is invalid."); }
            }
        }
    }

    private static void validateText(FormField field, JsonNode value) {
        String text = text(field, value);
        JsonNode config = field.getConfigurationJson();
        int min = config.path("minLength").asInt(0);
        int max = config.path("maxLength").asInt(MAX_TEXT);
        if (text.length() < min || text.length() > max || text.length() > MAX_TEXT) throw invalid(field.getFieldKey(), "Text length is outside the allowed range.");
        if (config.has("pattern") && !Pattern.compile(config.get("pattern").asText()).matcher(text).matches()) {
            throw invalid(field.getFieldKey(), "Value does not match the configured format.");
        }
    }

    private static void validateChoice(FormField field, JsonNode value, boolean multiple) {
        Set<String> allowed = new HashSet<>();
        field.getConfigurationJson().path("options").forEach(option -> allowed.add(option.asText().trim().toUpperCase(Locale.ROOT)));
        if (multiple) {
            if (!value.isArray() || value.size() > 100) throw invalid(field.getFieldKey(), "Select one or more valid options.");
            Set<String> selected = new HashSet<>();
            for (JsonNode item : value) {
                if (!item.isTextual()) throw invalid(field.getFieldKey(), "Selected options are invalid.");
                String normalized = item.asText().trim().toUpperCase(Locale.ROOT);
                if (!allowed.contains(normalized) || !selected.add(normalized)) throw invalid(field.getFieldKey(), "Selected options are invalid.");
            }
        } else {
            if (!value.isTextual() || !allowed.contains(value.asText().trim().toUpperCase(Locale.ROOT))) throw invalid(field.getFieldKey(), "Select a valid option.");
        }
    }

    private static String text(FormField field, JsonNode value) {
        if (!value.isTextual()) throw invalid(field.getFieldKey(), "Enter a text value.");
        return value.asText().trim();
    }

    private static ApiException invalid(String field, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "FORM_ANSWER_INVALID", field == null ? message : field + ": " + message);
    }

    /** Small immutable indexed view to avoid rebuilding lookup sets repeatedly. */
    public record ListView(java.util.List<FormField> values, Set<String> keys) {
        public ListView(java.util.List<FormField> values) {
            this(java.util.List.copyOf(values), values.stream().map(FormField::getFieldKey).collect(java.util.stream.Collectors.toUnmodifiableSet()));
        }
    }
}
