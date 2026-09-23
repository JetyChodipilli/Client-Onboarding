package com.brainserve.clientonboarding.forms.domain.model;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FormPolicyTest {
    private FormField field(String key,FormField.Type type,boolean required,FormField.Condition condition) {
        return new FormField(key,key,type,required,null,List.of(),null,null,null,condition);
    }
    @Test void requiredFieldsAndHiddenAnswersUseVisibleConditionsOnly() {
        var fields=List.of(field("company",FormField.Type.BOOLEAN,true,null),
                field("name",FormField.Type.TEXT,true,new FormField.Condition("company",FormField.Operator.EQUALS,"true")),
                field("details",FormField.Type.TEXT,true,new FormField.Condition("name",FormField.Operator.NOT_EQUALS,"other")));
        FormPolicy.validateDefinition(fields);
        assertThat(FormPolicy.answers(fields,Map.of("company",false,"name","hidden","details","hidden"),true)).containsOnlyKeys("company");
        assertThatThrownBy(() -> FormPolicy.answers(fields,Map.of("company",true),true)).isInstanceOf(FormPolicy.InvalidAnswer.class).hasMessage("This field is required.");
        assertThat(FormPolicy.answers(fields,Map.of(),false)).isEmpty();
    }
    @Test void graphAndInputBoundsRejectUnknownCyclesAndDuplicates() {
        assertThatThrownBy(() -> FormPolicy.validateDefinition(List.of(field("a",FormField.Type.TEXT,false,new FormField.Condition("b",FormField.Operator.EQUALS,"x")),field("b",FormField.Type.TEXT,false,null)))).isInstanceOf(FormPolicy.InvalidAnswer.class);
        var f=field("name",FormField.Type.TEXT,true,null);
        assertThatThrownBy(() -> FormPolicy.validateDefinition(List.of(f,f))).isInstanceOf(FormPolicy.InvalidAnswer.class);
        assertThatThrownBy(() -> FormPolicy.answers(List.of(f),Map.of("foreign","x"),false)).isInstanceOf(FormPolicy.InvalidAnswer.class);
        assertThatThrownBy(() -> FormPolicy.answers(List.of(f),Map.of("name","x".repeat(2001)),false)).isInstanceOf(FormPolicy.InvalidAnswer.class);
    }
    @Test void validatesFormatsWithoutExecutingOrFetchingUserContent() {
        for(var invalid:Map.of(FormField.Type.EMAIL,"bad",FormField.Type.URL,"javascript:alert(1)",FormField.Type.DATE,"2026-02-30").entrySet()) {
            var f=field("answer",invalid.getKey(),true,null);
            assertThat(FormPolicy.answers(List.of(f),Map.of("answer",invalid.getValue()),false)).isNotEmpty();
            assertThatThrownBy(() -> FormPolicy.answers(List.of(f),Map.of("answer",invalid.getValue()),true)).isInstanceOf(FormPolicy.InvalidAnswer.class);
        }
        assertThatThrownBy(() -> FormPolicy.answers(List.of(field("url",FormField.Type.URL,true,null)),Map.of("url","relative"),true)).isInstanceOf(FormPolicy.InvalidAnswer.class);
        var numeric=new FormField("budget","Budget",FormField.Type.NUMBER,true,null,List.of(),null,BigDecimal.ONE,BigDecimal.TEN,null);
        assertThatThrownBy(() -> FormPolicy.answers(List.of(numeric),Map.of("budget",11),true)).isInstanceOf(FormPolicy.InvalidAnswer.class);
        assertThat(FormPolicy.answers(List.of(numeric),Map.of("budget",5),true)).containsEntry("budget",5);
    }
    @Test void multipleChoiceMustUseConfiguredUniqueOptions() {
        var f=new FormField("channels","Channels",FormField.Type.MULTI_SELECT,true,null,List.of("Web","Email"),null,null,null,null);
        var detail=field("details",FormField.Type.TEXT,true,new FormField.Condition("channels",FormField.Operator.CONTAINS,"Web"));
        FormPolicy.validateDefinition(List.of(f,detail));
        assertThat(FormPolicy.answers(List.of(f,detail),Map.of("channels",List.of("Email")),true)).containsOnlyKeys("channels");
        assertThatThrownBy(() -> FormPolicy.answers(List.of(f),Map.of("channels",List.of("Web","Web")),true)).isInstanceOf(FormPolicy.InvalidAnswer.class);
        assertThatThrownBy(() -> FormPolicy.answers(List.of(f),Map.of("channels",List.of("Unknown")),false)).isInstanceOf(FormPolicy.InvalidAnswer.class);
    }
}
