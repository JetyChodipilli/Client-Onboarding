package com.brainserve.onboarding.access;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.brainserve.onboarding.access.application.service.PlatformAccessSafetyPolicy;
import com.brainserve.onboarding.common.error.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class PlatformAccessSafetyPolicyTest {
    private final PlatformAccessSafetyPolicy policy=new PlatformAccessSafetyPolicy();
    private final ObjectMapper json=new ObjectMapper();

    @Test void normalRoleBasedInstructionsAreAccepted() throws Exception {
        assertThatCode(()->policy.validateGuide("Invite agency@example.com as an Analyst. Never share your password.","https://support.example.com/access",json.readTree("[{\"type\":\"LINK\",\"label\":\"Official guide\",\"url\":\"https://support.example.com/roles\"}]"))).doesNotThrowAnyException();
    }
    @Test void guideCannotAskForThirdPartyCredentials(){
        assertThatThrownBy(()->policy.validateGuide("Please send us your password so we can log in.",null,json.createArrayNode())).isInstanceOf(ApiException.class).hasMessageContaining("must never ask");
    }
    @Test void clientSubmissionRejectsLikelySecrets(){
        assertThatThrownBy(()->policy.validateClientSubmission("account-123","password: SuperSecret123")).isInstanceOf(ApiException.class).hasMessageContaining("Do not submit passwords");
        assertThatThrownBy(()->policy.validateClientSubmission(null,"Bearer abcdefghijklmnopqrstuvwxyz.123")).isInstanceOf(ApiException.class);
    }
    @Test void embeddedCredentialsAndNonTlsExternalLinksAreRejected() throws Exception {
        assertThatThrownBy(()->policy.validateGuide("Invite the project user by email.","https://user:pass@example.com/help",json.createArrayNode())).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->policy.validateGuide("Invite the project user by email.",null,json.readTree("[{\"type\":\"LINK\",\"label\":\"Guide\",\"url\":\"http://example.com/help\"}]"))).isInstanceOf(ApiException.class);
    }
}
