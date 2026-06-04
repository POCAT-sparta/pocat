package com.rocketcrew.pocat.global.monitoring;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "pocat.monitoring")
public class AuctionAnomalyProperties {
    private double auctionAnomalyThreshold = 3.0;
}
