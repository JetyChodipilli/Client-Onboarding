package com.brainserve.onboarding.payments.infrastructure.persistence;
import com.brainserve.onboarding.payments.domain.model.WebhookEvent;import java.util.*;import org.springframework.data.jpa.repository.JpaRepository;
public interface WebhookEventRepository extends JpaRepository<WebhookEvent,UUID>{Optional<WebhookEvent> findByProviderAndProviderEventId(String provider,String providerEventId);}
