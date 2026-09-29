package com.brainserve.clientonboarding.payments.infrastructure;

import java.util.*;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Deployment secret configuration; never persist or return this object. */
@ConfigurationProperties("app.payments")
public class RazorpayProperties {
    private boolean enabled;
    private List<Account> accounts=List.of();
    public boolean isEnabled(){return enabled;} public void setEnabled(boolean enabled){this.enabled=enabled;}
    public List<Account> getAccounts(){return accounts;} public void setAccounts(List<Account> accounts){this.accounts=List.copyOf(accounts);}
    public void validate(){
        if(!enabled)return;
        if(accounts.isEmpty())throw new IllegalStateException("Configure at least one Razorpay account when payments are enabled");
        Set<UUID> orgs=new HashSet<>();Set<String> keys=new HashSet<>();
        for(var a:accounts)if(a.organizationId==null || !orgs.add(a.organizationId) || a.keyId==null || !a.keyId.matches("rzp_(test|live)_[A-Za-z0-9]+") || !keys.add(a.keyId) || a.keySecret==null || a.keySecret.length()<16 || a.webhookSecret==null || a.webhookSecret.length()<24 || (a.previousWebhookSecret!=null && a.previousWebhookSecret.length()<24))throw new IllegalStateException("Invalid or duplicate Razorpay account configuration");
    }
    public Optional<Account> account(UUID org){return enabled?accounts.stream().filter(a->a.organizationId.equals(org)).findFirst():Optional.empty();}
    public static class Account {
        private UUID organizationId;private String keyId,keySecret,webhookSecret,previousWebhookSecret;
        public UUID getOrganizationId(){return organizationId;}public void setOrganizationId(UUID v){organizationId=v;}
        public String getKeyId(){return keyId;}public void setKeyId(String v){keyId=v;}
        public String getKeySecret(){return keySecret;}public void setKeySecret(String v){keySecret=v;}
        public String getWebhookSecret(){return webhookSecret;}public void setWebhookSecret(String v){webhookSecret=v;}
        public String getPreviousWebhookSecret(){return previousWebhookSecret;}public void setPreviousWebhookSecret(String v){previousWebhookSecret=v;}
    }
}
