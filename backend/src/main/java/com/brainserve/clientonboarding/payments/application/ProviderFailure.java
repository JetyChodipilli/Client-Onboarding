package com.brainserve.clientonboarding.payments.application;

import com.brainserve.clientonboarding.common.error.DomainException;
import org.springframework.http.HttpStatus;

public class ProviderFailure extends DomainException {
    private final boolean definite;
    public ProviderFailure(boolean definite){super("PAYMENT_PROVIDER_UNAVAILABLE",definite?"The payment provider rejected this request. Contact your project team.":"The payment provider could not confirm this request. Its status must be reconciled before retrying.",HttpStatus.BAD_GATEWAY);this.definite=definite;}
    public boolean definite(){return definite;}
}
