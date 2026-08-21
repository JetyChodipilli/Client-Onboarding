package com.brainserve.onboarding.auth.application.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class SecurityMailService {
    private final JavaMailSender mailSender;
    private final String from;
    private final String frontendBaseUrl;

    public SecurityMailService(JavaMailSender mailSender,
                               @Value("${app.mail.from:no-reply@example.invalid}") String from,
                               @Value("${app.mail.frontend-base-url:http://localhost:3000}") String frontendBaseUrl) {
        this.mailSender = mailSender;
        this.from = from;
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
    }

    public void sendPasswordReset(String recipient, String token) {
        send(recipient, "Reset your password",
                "A password reset was requested for your account.\n\n" +
                frontendBaseUrl + "/reset-password?token=" + token +
                "\n\nIf you did not request this, you can ignore this message.");
    }

    public void sendEmailVerification(String recipient, String token) {
        send(recipient, "Verify your email",
                "Verify your email address to continue.\n\n" +
                frontendBaseUrl + "/verify-email?token=" + token);
    }

    public void sendOrganizationInvitation(String recipient, String organizationName, String token) {
        String safeOrganizationName = organizationName.replaceAll("[\r\n]+", " ").trim();
        send(recipient, "You are invited to " + safeOrganizationName,
                "You have been invited to join " + safeOrganizationName + ".\n\n" +
                frontendBaseUrl + "/accept-invitation?token=" + token);
    }

    public void sendClientInvitation(String recipient, String organizationName, String projectName, String token) {
        String safeOrganizationName = organizationName.replaceAll("[\r\n]+", " ").trim();
        String safeProjectName = projectName.replaceAll("[\r\n]+", " ").trim();
        send(recipient, "Your " + safeOrganizationName + " onboarding is ready",
                safeOrganizationName + " invited you to the client onboarding portal for " + safeProjectName + ".\n\n" +
                frontendBaseUrl + "/portal/invitations/accept?token=" + token +
                "\n\nThis secure link expires and may be revoked or replaced by a newer invitation.");
    }

    public void sendClientPasswordReset(String recipient, String token) {
        send(recipient, "Reset your client portal password",
                "A password reset was requested for your client portal account.\n\n" +
                frontendBaseUrl + "/portal/reset-password?token=" + token +
                "\n\nIf you did not request this, you can ignore this message.");
    }

    private void send(String recipient, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }
}
