package com.rocketcrew.pocat.global.time;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class KoreaTimeSerializerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesUtcLocalDateTimeAsKoreaOffsetTime() throws Exception {
        TimeResponse response = new TimeResponse(LocalDateTime.of(2026, 6, 15, 10, 0));

        assertThat(objectMapper.writeValueAsString(response))
                .isEqualTo("{\"time\":\"2026-06-15T19:00:00+09:00\"}");
    }

    private record TimeResponse(
            @JsonSerialize(using = KoreaTimeSerializer.class) LocalDateTime time
    ) {
    }
}
