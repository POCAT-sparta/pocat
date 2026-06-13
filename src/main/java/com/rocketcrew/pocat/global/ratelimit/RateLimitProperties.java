package com.rocketcrew.pocat.global.ratelimit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {
    private int signupLimit = 5;
    private long signupWindowSeconds = 60;
    private int reissueLimit = 5;
    private long reissueWindowSeconds = 60;
    private int postLimit = 5;
    private long postWindowSeconds = 60;
    private int commentLimit = 5;
    private long commentWindowSeconds = 60;
    private int searchLimit = 30;
    private long searchWindowSeconds = 60;
    private int likeLimit = 10;
    private long likeWindowSeconds = 60;

    // H2 — login
    private int loginLimit = 10;
    private long loginWindowSeconds = 60;

    // M2 — 도메인별
    private int auctionLimit = 10;
    private long auctionWindowSeconds = 60;

    private int bidLimit = 30;
    private long bidWindowSeconds = 60;

    private int chatLimit = 20;
    private long chatWindowSeconds = 60;

    private int orderLimit = 10;
    private long orderWindowSeconds = 60;

    private int paymentLimit = 10;
    private long paymentWindowSeconds = 60;

    private int refundLimit = 5;
    private long refundWindowSeconds = 60;

    private int cardLimit = 10;
    private long cardWindowSeconds = 60;

    private int userLimit = 10;
    private long userWindowSeconds = 60;

    private int aiLimit = 10;
    private long aiWindowSeconds = 60;

    private int aiEmbeddingLimit = 80;
    private long aiEmbeddingWindowSeconds = 60;
}
