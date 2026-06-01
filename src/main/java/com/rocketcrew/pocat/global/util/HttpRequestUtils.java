package com.rocketcrew.pocat.global.util;

import jakarta.servlet.http.HttpServletRequest;

public class HttpRequestUtils {

    private HttpRequestUtils() {}

    public static String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff == null || xff.isBlank()) {
            return normalize(request.getRemoteAddr());
        }
        String[] parts = xff.split(",");
        for (int i = parts.length - 1; i >= 0; i--) {
            String candidate = parts[i].trim();
            if (!candidate.isEmpty()) {
                return normalize(candidate);
            }
        }
        return normalize(request.getRemoteAddr());
    }

    private static String normalize(String ip) {
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            return "127.0.0.1";
        }
        return ip;
    }
}
