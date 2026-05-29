package com.rocketcrew.pocat.global.util;

import jakarta.servlet.http.HttpServletRequest;

public class HttpRequestUtils {

    private HttpRequestUtils() {}

    public static String resolveClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        }
        String[] parts = ip.split(",");
        ip = parts[parts.length - 1].trim();
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            return "127.0.0.1";
        }
        return ip;
    }
}
