package com.brainserve.clientonboarding.billing.domain.model;

import static org.assertj.core.api.Assertions.*;
import static com.brainserve.clientonboarding.billing.domain.model.BillingModels.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class InvoicePolicyTest {
    @Test void quantitiesTaxHalfUpAndCapsAreExact(){
        var t=InvoicePolicy.calculate(List.of(new ItemInput("A",1,1,5000),new ItemInput("B",3,1999,1800)));
        assertThat(t.subtotalMinor()).isEqualTo(5998);assertThat(t.taxMinor()).isEqualTo(1080);assertThat(t.totalMinor()).isEqualTo(7078);
        assertThatThrownBy(()->InvoicePolicy.calculate(List.of(new ItemInput("Overflow",10000,InvoicePolicy.MAX_MINOR,0)))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->InvoicePolicy.calculate(List.of(new ItemInput("Negative",1,-1,0)))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->InvoicePolicy.calculate(List.of(new ItemInput("Tax",1,100,10001)))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void allPoliciesHaveExplicitThresholdsAndDepositsRoundUp(){
        assertThat(InvoicePolicy.threshold(Map.of("paymentPolicy","FULL"),101)).isEqualTo(101);
        assertThat(InvoicePolicy.threshold(Map.of("paymentPolicy","MANUAL"),101)).isEqualTo(101);
        assertThat(InvoicePolicy.threshold(Map.of("paymentPolicy","DEPOSIT","depositPercent",50),101)).isEqualTo(51);
        assertThat(InvoicePolicy.threshold(Map.of("paymentPolicy","MILESTONE","milestoneAmountMinor",25),101)).isEqualTo(25);
        assertThat(InvoicePolicy.threshold(Map.of("paymentPolicy","NO_PAYMENT_REQUIRED"),0)).isZero();
        assertThatThrownBy(()->InvoicePolicy.threshold(Map.of("paymentPolicy","MILESTONE","milestoneAmountMinor",200),100)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->InvoicePolicy.validate(Map.of("paymentPolicy","DEPOSIT","depositPercent",50.5))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->InvoicePolicy.validate(Map.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
