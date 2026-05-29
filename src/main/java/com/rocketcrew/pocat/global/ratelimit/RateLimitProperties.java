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
}
