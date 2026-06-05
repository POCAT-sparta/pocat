package com.rocketcrew.pocat.global.config;

import com.rocketcrew.pocat.global.filter.MdcLoggingFilter;
import com.rocketcrew.pocat.global.security.JwtUtil;
import com.rocketcrew.pocat.global.security.JwtAuthenticationFilter;
import com.rocketcrew.pocat.global.security.InternalTokenAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;
    private final Environment environment;

    @Value("${cors.allowed-origins:http://localhost:*}")
    private String allowedOrigins;

    @Value("${pocat.internal.token}")
    private String internalToken;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        config.setAllowedOriginPatterns(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth
                            .requestMatchers("/internal/**").authenticated()
                            .requestMatchers("/api/v1/auth/**").permitAll()
                            .requestMatchers("/ws/chat/**").permitAll()
                            .requestMatchers(HttpMethod.GET,
                                    "/api/v1/auctions/me",
                                    "/api/v1/bids/me",
                                    "/api/v1/posts/free/me",
                                    "/api/v1/likes/me").authenticated()
                            .requestMatchers(HttpMethod.GET,
                                    "/api/v1/auctions/**",
                                    "/api/v1/cards/**",
                                    "/api/v1/posts/free/**",
                                    "/api/v1/posts/trade/**",
                                    "/api/v1/comments/**").permitAll()
                            // PortOne 서버가 직접 호출하는 Webhook — JWT 인증 없음
                            // X-PortOne-Signature HMAC-SHA256 서명 검증은 PortOneSignatureVerifier에서 완전 구현됨
                            .requestMatchers(HttpMethod.POST, "/api/v1/payments/webhook").permitAll()
                            .requestMatchers("/actuator/health", "/actuator/prometheus").permitAll()
                            .requestMatchers("/api/v1/admin/**").hasRole("ADMIN");
                    if (environment.matchesProfiles("local")) {
                        auth.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll();
                    } else {
                        auth.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").denyAll();
                    }
                    auth.anyRequest().authenticated();
                })
                .addFilterBefore(
                        new InternalTokenAuthFilter(internalToken),
                        UsernamePasswordAuthenticationFilter.class
                )
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtUtil, redisTemplate),
                        UsernamePasswordAuthenticationFilter.class
                )
                .addFilterAfter(
                        new MdcLoggingFilter(),
                        JwtAuthenticationFilter.class
                );

        return http.build();
    }
}
