package com.rocketcrew.pocat.domain.payment.client;

import com.rocketcrew.pocat.global.config.PortOneProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;

@Component
@RequiredArgsConstructor
public class PortOneSignatureVerifier {

    private final PortOneProperties portOneProperties;

    public boolean verify(String signatureHeader, byte[] rawBody) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    portOneProperties.webhookSecret().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            ));
            String computed = HexFormat.of().formatHex(mac.doFinal(rawBody));

            // 헤더 형식: "v1=abc123" 또는 "v1=abc123,v1=def456" (여러 개일 수 있음)
            return Arrays.stream(signatureHeader.split(","))
                    .map(String::trim)
                    .filter(s -> s.startsWith("v1="))
                    .map(s -> s.substring(3))
                    .anyMatch(hash -> MessageDigest.isEqual(
                            hash.getBytes(StandardCharsets.UTF_8),
                            computed.getBytes(StandardCharsets.UTF_8)
                    ));
        } catch (Exception e) {
            return false;
        }
    }
}
