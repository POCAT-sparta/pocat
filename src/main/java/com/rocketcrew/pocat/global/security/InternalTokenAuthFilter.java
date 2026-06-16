package com.rocketcrew.pocat.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;

@Slf4j
public class InternalTokenAuthFilter extends OncePerRequestFilter {

    private final String internalToken;

    public InternalTokenAuthFilter(String internalToken) {
        this.internalToken = internalToken;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = request.getHeader("X-Internal-Token");

        if (!StringUtils.hasText(internalToken)
                || !StringUtils.hasText(token)
                || !MessageDigest.isEqual(
                internalToken.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8))) {
            log.warn("[INTERNAL_AUTH] unauthorized uri={} reason={}",
                    request.getRequestURI(), unauthorizedReason(token));
            // TEMPORARY LOCAL DEBUG ONLY: restore the 401 response before commit/deploy.
            // response.setStatus(HttpStatus.UNAUTHORIZED.value());
            // return;
        }

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "batch-server", null, Collections.emptyList()
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
        filterChain.doFilter(request, response);
    }

    private String unauthorizedReason(String token) {
        if (!StringUtils.hasText(internalToken)) {
            return "configured_token_missing";
        }
        if (!StringUtils.hasText(token)) {
            return "request_token_missing";
        }
        if (internalToken.length() != token.length()) {
            return "length_mismatch configuredLength=" + internalToken.length()
                    + " requestLength=" + token.length();
        }
        return "value_mismatch length=" + token.length();
    }
}
