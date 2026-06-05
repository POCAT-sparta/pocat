package com.rocketcrew.pocat.global.filter;

import com.rocketcrew.pocat.global.security.CustomUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

public class MdcLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            MDC.put("requestId", UUID.randomUUID().toString().replace("-", "").substring(0, 12));
            MDC.put("method", request.getMethod());
            MDC.put("uri", request.getRequestURI());

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof CustomUserDetails userDetails) {
                MDC.put("userId", String.valueOf(userDetails.getUserId()));
            }

            filterChain.doFilter(request, response);
            MDC.put("status", String.valueOf(response.getStatus()));
        } finally {
            MDC.clear();
        }
    }
}
