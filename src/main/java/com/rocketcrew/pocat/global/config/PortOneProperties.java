package com.rocketcrew.pocat.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "portone")
public record PortOneProperties(
        String secret,
        String webhookSecret
) {}
