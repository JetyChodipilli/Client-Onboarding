package com.brainserve.clientonboarding.auth.application;

public interface SecurityNotificationPort {
    void sendVerification(String recipient, String displayName, String verificationUrl);
    void sendPasswordReset(String recipient, String displayName, String resetUrl);
    void sendOrganizationInvitation(String recipient, String displayName, String organizationName,
                                    String invitationUrl);
}
