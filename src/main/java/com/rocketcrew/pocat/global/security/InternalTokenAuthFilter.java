package com.rocketcrew.pocat.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;

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

//        if (!StringUtils.hasText(token) || !MessageDigest.isEqual(
//                internalToken.getBytes(StandardCharsets.UTF_8),
//                token.getBytes(StandardCharsets.UTF_8))) {
//            response.setStatus(HttpStatus.UNAUTHORIZED.value());
//            return;
//        }

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "batch-server", null, Collections.emptyList()
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
        filterChain.doFilter(request, response);
    }
}
