package com.rocketcrew.pocat.domain.auction.ranking.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "ranking.auction")
public class AuctionRankingProperties {
    @Positive
    private double likeWeight = 1.0;
    @Positive
    private double bidWeight = 3.0;
    @Min(1)
    private int ttlSeconds = 70;
    @Min(1)
    private int cacheSize = 100;
    @Min(1)
    private int maxResponseSize = 50;
}