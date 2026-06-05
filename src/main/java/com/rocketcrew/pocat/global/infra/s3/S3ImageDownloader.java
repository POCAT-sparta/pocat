package com.rocketcrew.pocat.global.infra.s3;

import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.CardException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
public class S3ImageDownloader {

    private static final String DEFAULT_CONTENT_TYPE = "image/jpeg";
    private static final int MAX_SIZE_BYTES = 10 * 1024 * 1024; // 10MB

    private final RestTemplate restTemplate;

    public S3ImageDownloader() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(15_000);
        this.restTemplate = new RestTemplate(factory);
    }

    S3ImageDownloader(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public record DownloadResult(byte[] bytes, String contentType) {}

    public DownloadResult download(String imageUrl) {
        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    imageUrl, HttpMethod.GET, null, byte[].class);
            byte[] bytes = response.getBody();
            if (bytes == null || bytes.length == 0) {
                throw new CardException(ErrorCode.CARD_IMAGE_DOWNLOAD_FAILED);
            }
            if (bytes.length > MAX_SIZE_BYTES) {
                throw new CardException(ErrorCode.CARD_IMAGE_DOWNLOAD_FAILED);
            }
            String contentType = response.getHeaders().getContentType() != null
                    ? response.getHeaders().getContentType().toString()
                    : DEFAULT_CONTENT_TYPE;
            return new DownloadResult(bytes, contentType);
        } catch (CardException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[S3ImageDownloader] 다운로드 실패 url={}", imageUrl, e);
            throw new CardException(ErrorCode.CARD_IMAGE_DOWNLOAD_FAILED);
        }
    }
}
