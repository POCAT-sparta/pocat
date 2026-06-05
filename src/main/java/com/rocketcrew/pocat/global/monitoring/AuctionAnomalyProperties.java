package com.rocketcrew.pocat.global.monitoring;

import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "pocat.monitoring")
@Validated
public class AuctionAnomalyProperties {
    @Positive
    private double auctionAnomalyThreshold = 3.0;
}
