package com.rocketcrew.pocat.global.filter;

import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.common.ServiceException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class BadWordFilterService {

    private final Set<String> badWords;

    public BadWordFilterService() {
        try {
            ClassPathResource resource = new ClassPathResource("badwords.txt");
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                this.badWords = reader.lines()
                        .map(String::trim)
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .map(w -> Normalizer.normalize(w, Normalizer.Form.NFC).toLowerCase(Locale.ROOT))
                        .collect(Collectors.toUnmodifiableSet());
            }
        } catch (IOException e) {
            throw new IllegalStateException("badwords.txt 로드 실패", e);
        }
    }

    public void validate(String... texts) {
        for (String text : texts) {
            if (text == null || text.isBlank()) continue;
            String lower = Normalizer.normalize(text, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
            for (String word : badWords) {
                if (lower.contains(word)) {
                    throw new ServiceException(ErrorCode.CONTAINS_BAD_WORD);
                }
            }
        }
    }
}
