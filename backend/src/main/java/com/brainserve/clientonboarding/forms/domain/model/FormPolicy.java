package com.brainserve.clientonboarding.forms.domain.model;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Bounded declarative fields; conditions only reference earlier, visible answers. No scripts or user regex. */
public final class FormPolicy {
    private FormPolicy() { }
    public static void validateDefinition(List<FormField> fields) {
        if (fields == null || fields.isEmpty() || fields.size() > 100) fail("fields", "Use 1 to 100 fields.");
        Map<String,FormField> earlier = new HashMap<>();
        for (FormField field : fields) {
            if (field == null || field.key() == null || !field.key().matches("[a-z][a-z0-9_]{0,59}")
                    || earlier.containsKey(field.key())) fail("fields", "Field keys must be unique lowercase identifiers.");
            String key = field.key();
            if (field.type() == null || field.label() == null || field.label().isBlank() || field.label().length() > 180
                    || field.helpText() != null && field.helpText().length() > 1000) fail(key, "Provide a type and a label of at most 180 characters.");
            boolean choice = Set.of(FormField.Type.DROPDOWN, FormField.Type.RADIO, FormField.Type.CHECKBOX,
                    FormField.Type.MULTI_SELECT).contains(field.type());
            if (choice && (field.options().isEmpty() || field.options().size() > 50)
                    || !choice && !field.options().isEmpty()
                    || field.options().stream().anyMatch(v -> v.isBlank() || v.length() > 200)
                    || new HashSet<>(field.options()).size() != field.options().size()) fail(key, "Provide 1 to 50 unique options only for choice fields.");
            if (field.maxLength() != null && (field.maxLength() < 1 || field.maxLength() > 10000)) fail(key, "Maximum length must be 1 to 10000.");
            if (field.min() != null && field.max() != null && field.min().compareTo(field.max()) > 0) fail(key, "Minimum must not exceed maximum.");
            if (field.type() != FormField.Type.NUMBER && (field.min() != null || field.max() != null)) fail(key, "Numeric limits require a number field.");
            if (field.condition() != null) {
                var c = field.condition();
                if (!earlier.containsKey(c.fieldKey()) || c.operator() == null || c.value() == null || c.value().length() > 200)
                    fail(key, "Conditions must reference an earlier field and a value of at most 200 characters.");
                var source = earlier.get(c.fieldKey());
                boolean multi = source.type() == FormField.Type.CHECKBOX || source.type() == FormField.Type.MULTI_SELECT;
                if (multi != (c.operator() == FormField.Operator.CONTAINS)) fail(key, "Use CONTAINS for multiple-choice conditions and EQUALS or NOT_EQUALS otherwise.");
            }
            earlier.put(key, field);
        }
    }

    public static Map<String,Object> answers(List<FormField> fields, Map<String,Object> answers, boolean submit) {
        if (answers == null || answers.size() > 100) fail("answers", "Provide at most 100 answers.");
        Set<String> keys = new HashSet<>();
        fields.forEach(f -> keys.add(f.key()));
        if (!keys.containsAll(answers.keySet())) fail("answers", "An answer refers to an unknown field.");
        Map<String,Object> visible = new LinkedHashMap<>();
        for (FormField f : fields) {
            if (!visible(f, visible)) continue;
            Object value = answers.get(f.key());
            if (value == null || value instanceof String s && s.isBlank() || value instanceof List<?> l && l.isEmpty()) {
                if (submit && f.required()) fail(f.key(), "This field is required.");
                continue;
            }
            boolean valid = switch (f.type()) {
                case BOOLEAN -> value instanceof Boolean;
                case NUMBER -> value instanceof Number && value.toString().length() <= 100;
                case CHECKBOX, MULTI_SELECT -> value instanceof List<?> list && list.size() <= 50
                        && list.stream().allMatch(v -> v instanceof String && f.options().contains(v))
                        && new HashSet<>(list).size() == list.size();
                case DROPDOWN, RADIO -> value instanceof String && f.options().contains(value);
                default -> value instanceof String s && s.length() <= (f.maxLength() == null ? 2000 : f.maxLength());
            };
            if (!valid) fail(f.key(), "The answer has an invalid type, length, or option.");
            if (submit) validateFormat(f, value);
            visible.put(f.key(), value);
        }
        return Map.copyOf(visible);
    }

    public static boolean visible(FormField field, Map<String,Object> visibleAnswers) {
        var c = field.condition();
        if (c == null) return true;
        Object value = visibleAnswers.get(c.fieldKey());
        if (value == null) return false;
        return switch (c.operator()) {
            case EQUALS -> value.toString().equals(c.value());
            case NOT_EQUALS -> !value.toString().equals(c.value());
            case CONTAINS -> value instanceof List<?> values && values.contains(c.value());
        };
    }

    private static void validateFormat(FormField f, Object value) {
        try {
            switch (f.type()) {
                case NUMBER -> {
                    BigDecimal n = new BigDecimal(value.toString());
                    if (f.min() != null && n.compareTo(f.min()) < 0 || f.max() != null && n.compareTo(f.max()) > 0)
                        fail(f.key(), "Enter a number within the allowed range.");
                }
                case DATE -> { if (!value.toString().matches("\\d{4}-\\d{2}-\\d{2}")) fail(f.key(), "Use a valid date."); LocalDate.parse(value.toString()); }
                case EMAIL -> { if (!value.toString().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || value.toString().length() > 254) fail(f.key(), "Enter a valid email address."); }
                case URL -> { URI uri = URI.create(value.toString()); if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) || uri.getHost() == null || uri.getUserInfo() != null) fail(f.key(), "Enter an http or https URL without credentials."); }
                default -> { }
            }
        } catch (java.time.DateTimeException | IllegalArgumentException e) {
            if (e instanceof InvalidAnswer invalid) throw invalid;
            fail(f.key(), "Enter a valid " + f.type().name().toLowerCase() + " value.");
        }
    }
    private static void fail(String key, String message) { throw new InvalidAnswer(key, message); }
    public static final class InvalidAnswer extends IllegalArgumentException {
        private final String field;
        public InvalidAnswer(String field, String message) { super(message); this.field = field; }
        public String field() { return field; }
    }
}
