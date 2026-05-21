package com.rocketcrew.pocat.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;

@Configuration
@EnableWebSocketSecurity
public class WebSocketSecurityConfig {

    @Bean
    AuthorizationManager<Message<?>> messageAuthorizationManager(
            MessageMatcherDelegatingAuthorizationManager.Builder messages) {

        messages
                .nullDestMatcher().permitAll()
                .simpSubscribeDestMatchers("/sub/**").authenticated()
                .simpDestMatchers("/pub/**").authenticated()
                .anyMessage().permitAll();

        return messages.build();
    }

    // JWT 기반 인증 사용 → WebSocket CSRF 불필요 → no-op으로 비활성화
    @Bean
    ChannelInterceptor csrfChannelInterceptor() {
        return new ChannelInterceptor() {};
    }
}