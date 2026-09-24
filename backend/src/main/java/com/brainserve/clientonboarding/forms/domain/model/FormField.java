package com.brainserve.clientonboarding.forms.domain.model;

import java.math.BigDecimal;
import java.util.List;

public record FormField(String key, String label, Type type, boolean required, String helpText,
                        List<String> options, Integer maxLength, BigDecimal min, BigDecimal max,
                        Condition condition) {
    public enum Type { TEXT, TEXTAREA, NUMBER, EMAIL, URL, DATE, DROPDOWN, RADIO, CHECKBOX, MULTI_SELECT, BOOLEAN }
    public enum Operator { EQUALS, NOT_EQUALS, CONTAINS }
    public record Condition(String fieldKey, Operator operator, String value) { }
    public FormField { options = options == null ? List.of() : List.copyOf(options); }
}
