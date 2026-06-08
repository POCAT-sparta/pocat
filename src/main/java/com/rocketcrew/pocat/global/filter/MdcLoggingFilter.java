package com.rocketcrew.pocat.global.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
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
            // userId는 JwtAuthenticationFilter 인증 성공 시 설정됨
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
