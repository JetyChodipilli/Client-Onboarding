package com.brainserve.onboarding.payments.infrastructure.provider;
import com.brainserve.onboarding.common.error.ApiException;import com.brainserve.onboarding.payments.infrastructure.config.PaymentProperties;import java.util.*;import org.springframework.http.HttpStatus;import org.springframework.stereotype.Service;
@Service
public class PaymentGatewayRegistry{
 private final Map<String,PaymentGateway> gateways;private final PaymentProperties properties;
 public PaymentGatewayRegistry(List<PaymentGateway> values,PaymentProperties properties){Map<String,PaymentGateway> m=new HashMap<>();for(PaymentGateway g:values)m.put(g.providerCode().toUpperCase(Locale.ROOT),g);this.gateways=Map.copyOf(m);this.properties=properties;}
 public PaymentGateway current(){PaymentGateway g=gateways.get(properties.provider());if(g==null||!g.available())throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"PAYMENT_PROVIDER_UNAVAILABLE","Payment processing is temporarily unavailable. Please try again later.");return g;}
 public PaymentGateway byCode(String code){PaymentGateway g=gateways.get(code==null?"":code.trim().toUpperCase(Locale.ROOT));if(g==null||!g.available())throw new ApiException(HttpStatus.NOT_FOUND,"PAYMENT_PROVIDER_NOT_FOUND","Payment provider was not found.");return g;}
 public String configuredProvider(){return properties.provider();}
 public boolean isAvailable(String code){PaymentGateway g=gateways.get(code==null?"":code.trim().toUpperCase(Locale.ROOT));return g!=null&&g.available();}
}
