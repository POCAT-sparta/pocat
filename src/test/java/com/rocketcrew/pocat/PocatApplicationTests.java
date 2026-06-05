package com.rocketcrew.pocat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@Disabled("Redis 인프라 필요 — 단위 테스트 환경에서는 스킵")
@Tag("bulk")
@SpringBootTest
class PocatApplicationTests {

    @Test
    void contextLoads() {
    }

}
