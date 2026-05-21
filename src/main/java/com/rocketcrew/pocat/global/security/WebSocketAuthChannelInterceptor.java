package com.rocketcrew.pocat.global.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) return message;

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = resolveToken(accessor);
            if (!StringUtils.hasText(token)) {
                throw new MessageDeliveryException("Missing JWT token");
            }
            JwtUtil.TokenPayload payload = jwtUtil.extractPayload(token)
                    .orElseThrow(() -> new MessageDeliveryException("Invalid JWT token"));
            CustomUserDetails userDetails = new CustomUserDetails(payload.userId(), payload.role());
            Authentication auth = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());
            accessor.setUser(auth);
            setSecurityContext(auth);
        } else if (accessor.getUser() instanceof Authentication auth) {
            setSecurityContext(auth);
        }

        return message;
    }

    @Override
    public void afterSendCompletion(Message<?> message, MessageChannel channel, boolean sent, Exception ex) {
        SecurityContextHolder.clearContext();
    }

    private void setSecurityContext(Authentication auth) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
    }

    private String resolveToken(StompHeaderAccessor accessor) {
        String bearer = accessor.getFirstNativeHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        String token = accessor.getFirstNativeHeader("token");
        if (StringUtils.hasText(token)) {
            return token;
        }
        return null;
    }
}
