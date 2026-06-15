package com.rocketcrew.pocat.global.time;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class KoreaTimeSerializer extends JsonSerializer<LocalDateTime> {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    @Override
    public void serialize(LocalDateTime value, JsonGenerator generator, SerializerProvider serializers)
            throws IOException {
        generator.writeString(value.atOffset(ZoneOffset.UTC)
                .atZoneSameInstant(KOREA_ZONE)
                .toOffsetDateTime()
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    }
}
