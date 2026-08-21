package com.brainserve.onboarding.contracts.infrastructure.provider;
import com.brainserve.onboarding.common.error.ApiException;import com.brainserve.onboarding.contracts.infrastructure.config.ContractProperties;import java.util.*;import org.springframework.http.HttpStatus;import org.springframework.stereotype.Service;
@Service
public class ESignatureProviderRegistry{
 private final Map<String,ESignatureProvider> providers;private final ContractProperties props;
 public ESignatureProviderRegistry(List<ESignatureProvider> values,ContractProperties props){Map<String,ESignatureProvider> map=new HashMap<>();for(var p:values)map.put(p.providerCode().toUpperCase(Locale.ROOT),p);this.providers=Map.copyOf(map);this.props=props;}
 public ESignatureProvider current(){return byCode(props.provider());}
 public ESignatureProvider byCode(String code){var p=providers.get(code==null?"":code.trim().toUpperCase(Locale.ROOT));if(p==null||!p.available())throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"CONTRACT_PROVIDER_UNAVAILABLE","E-signature processing is temporarily unavailable.");return p;}
 public boolean isAvailable(String code){var p=providers.get(code==null?"":code.trim().toUpperCase(Locale.ROOT));return p!=null&&p.available();}
}
