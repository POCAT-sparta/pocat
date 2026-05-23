package com.rocketcrew.pocat.global.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

public class CacheControlInterceptor implements HandlerInterceptor {

    private static final String CACHE_CONTROL = "Cache-Control";

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response,
                           Object handler, ModelAndView modelAndView) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return;
        }

        String path = request.getRequestURI();

        if (isPrivatePath(path)) {
            response.setHeader(CACHE_CONTROL, "private, no-store");
            return;
        }

        if (isPublicCacheablePath(path)) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            boolean isAuthenticated = auth != null && auth.isAuthenticated()
                    && !"anonymousUser".equals(auth.getPrincipal());
            if (isAuthenticated) {
                response.setHeader(CACHE_CONTROL, "private, no-store");
            } else {
                response.setHeader(CACHE_CONTROL, "public, max-age=60");
            }
        }
    }

    private boolean isPublicCacheablePath(String path) {
        return path.matches("/api/v1/posts/free/\\d+")
                || path.matches("/api/v1/posts/trade/\\d+")
                || path.equals("/api/v1/auctions")
                || path.equals("/api/v1/auctions/popular");
    }

    private boolean isPrivatePath(String path) {
        return path.startsWith("/api/v1/users/")
                || path.matches("/api/v1/auctions/\\d+/bids.*");
    }
}
