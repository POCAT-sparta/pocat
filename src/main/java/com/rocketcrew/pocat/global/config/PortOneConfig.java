package com.rocketcrew.pocat.global.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@RequiredArgsConstructor
public class PortOneConfig {

    private final PortOneProperties portOneProperties;

    @Bean
    public RestClient portOneRestClient() {
        return RestClient.builder()
                .baseUrl("https://api.portone.io")
                .defaultHeader("Authorization", "PortOne " + portOneProperties.secret())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
