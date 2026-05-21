package com.rocketcrew.pocat.domain.auction.ranking.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ranking.auction")
public class AuctionRankingProperties {
    private double likeWeight = 1.0;
    private double bidWeight = 3.0;
    private int ttlSeconds = 70;
    private int cacheSize = 100;
    private int maxResponseSize = 50;
}
