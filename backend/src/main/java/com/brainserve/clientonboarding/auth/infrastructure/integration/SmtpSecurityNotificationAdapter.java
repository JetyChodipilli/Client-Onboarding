package com.brainserve.clientonboarding.auth.infrastructure.integration;

import com.brainserve.clientonboarding.common.error.SecurityNotificationDeliveryException;
import com.brainserve.clientonboarding.auth.application.SecurityNotificationPort;
import com.brainserve.clientonboarding.auth.infrastructure.configuration.AuthProperties;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.SSLSocket;
import org.springframework.stereotype.Component;

@Component
public class SmtpSecurityNotificationAdapter implements SecurityNotificationPort {
    private static final int TIMEOUT_MILLIS = 8_000;
    private final AuthProperties properties;

    public SmtpSecurityNotificationAdapter(AuthProperties properties) {
        this.properties = properties;
    }

    @Override
    public void sendVerification(String recipient, String displayName, String verificationUrl) {
        send(recipient, "Activate your Client Onboarding account",
                "Hello " + displayName + ",\r\n\r\nActivate your account and choose a password:\r\n"
                        + verificationUrl + "\r\n\r\nThis link expires soon and can be used once.");
    }

    @Override
    public void sendPasswordReset(String recipient, String displayName, String resetUrl) {
        send(recipient, "Reset your Client Onboarding password",
                "Hello " + displayName + ",\r\n\r\nReset your password:\r\n" + resetUrl
                        + "\r\n\r\nIf you did not request this, no action is needed.");
    }

    @Override
    public void sendOrganizationInvitation(String recipient, String displayName, String organizationName,
                                           String invitationUrl) {
        send(recipient, "You are invited to " + organizationName,
                "Hello " + displayName + ",\r\n\r\nAccept your invitation to " + organizationName + ":\r\n"
                        + invitationUrl + "\r\n\r\nThis link expires soon and can be used once.");
    }

    @Override
    public void sendClientInvitation(String recipient, String displayName, String organizationName,
                                     String projectName, String invitationUrl) {
        send(recipient, "Your " + projectName + " onboarding is ready",
                "Hello " + displayName + ",\r\n\r\n" + organizationName
                        + " invited you to complete onboarding for " + projectName + ":\r\n"
                        + invitationUrl + "\r\n\r\nThis private link expires soon and can be used once.");
    }

    private void send(String recipient, String subject, String body) {
        validateHeader(recipient);
        validateHeader(subject);
        validateHeader(properties.mailFrom());
        try (Socket initial = new Socket()) {
            initial.connect(new InetSocketAddress(properties.smtpHost(), properties.smtpPort()), TIMEOUT_MILLIS);
            initial.setSoTimeout(TIMEOUT_MILLIS);
            Connection connection = new Connection(initial);
            connection.expect(220);
            connection.command("EHLO client-onboarding", 250);
            if (properties.smtpStarttls()) {
                connection.command("STARTTLS", 220);
                SSLSocket secured = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault())
                        .createSocket(initial, properties.smtpHost(), properties.smtpPort(), true);
                var sslParameters = secured.getSSLParameters();
                sslParameters.setEndpointIdentificationAlgorithm("HTTPS");
                secured.setSSLParameters(sslParameters);
                secured.setSoTimeout(TIMEOUT_MILLIS);
                secured.startHandshake();
                connection = new Connection(secured);
                connection.command("EHLO client-onboarding", 250);
            }
            if (properties.smtpAuth()) {
                connection.command("AUTH LOGIN", 334);
                connection.command(base64(properties.smtpUsername()), 334);
                connection.command(base64(properties.smtpPassword()), 235);
            }
            connection.command("MAIL FROM:<" + properties.mailFrom() + ">", 250);
            connection.command("RCPT TO:<" + recipient + ">", 250, 251);
            connection.command("DATA", 354);
            connection.write("From: " + properties.mailFrom() + "\r\nTo: " + recipient
                    + "\r\nSubject: " + subject + "\r\nMIME-Version: 1.0\r\n"
                    + "Content-Type: text/plain; charset=UTF-8\r\n\r\n" + dotStuff(body) + "\r\n.\r\n");
            connection.expect(250);
            connection.command("QUIT", 221);
            connection.close();
        } catch (IOException | RuntimeException exception) {
            throw new SecurityNotificationDeliveryException("SMTP security notification failed", exception);
        }
    }

    private String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String dotStuff(String value) {
        return value.replace("\r\n.", "\r\n..");
    }

    private void validateHeader(String value) {
        if (value == null || value.contains("\r") || value.contains("\n")) {
            throw new IllegalArgumentException("Invalid SMTP header value");
        }
    }

    private static final class Connection {
        private final Socket socket;
        private final BufferedReader reader;
        private final BufferedWriter writer;

        private Connection(Socket socket) throws IOException {
            this.socket = socket;
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        }

        private void command(String value, int... expected) throws IOException {
            write(value + "\r\n");
            expect(expected);
        }

        private void write(String value) throws IOException {
            writer.write(value);
            writer.flush();
        }

        private void expect(int... expected) throws IOException {
            String line = reader.readLine();
            if (line == null || line.length() < 3) throw new IOException("SMTP connection closed");
            int code = Integer.parseInt(line.substring(0, 3));
            while (line.length() > 3 && line.charAt(3) == '-') {
                line = reader.readLine();
                if (line == null) throw new IOException("SMTP connection closed");
            }
            for (int value : expected) if (code == value) return;
            throw new IOException("Unexpected SMTP response code: " + code);
        }

        private void close() throws IOException {
            socket.close();
        }
    }
}
