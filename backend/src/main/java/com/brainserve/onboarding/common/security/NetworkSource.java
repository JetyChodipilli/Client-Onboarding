package com.brainserve.onboarding.common.security;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;

public final class NetworkSource {

    private NetworkSource() {
    }

    public static String clientIp(HttpServletRequest request) {
        // Do not parse X-Forwarded-For here. In deployments behind a trusted proxy, Spring's
        // forward-header strategy is responsible for sanitizing forwarded headers first.
        String candidate = request.getRemoteAddr();
        try {
            return InetAddress.getByName(candidate).getHostAddress();
        } catch (UnknownHostException ex) {
            return null;
        }
    }
}
