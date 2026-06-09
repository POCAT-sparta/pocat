package com.rocketcrew.pocat.global.infra.s3;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Slf4j
@Component
public class S3UploaderImpl implements S3Uploader {

    private final S3Client s3Client;
    private final String bucket;
    private final String region;

    public S3UploaderImpl(
            @Value("${cloud.aws.region.static}") String region,
            @Value("${cloud.aws.s3.bucket}") String bucket
    ) {
        this.bucket = bucket;
        this.region = region;

        this.s3Client = S3Client.builder()
                .region(Region.of(region))
                .build();
    }

    @Override
    public String upload(String key, byte[] data, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(data));

        String url = "https://%s.s3.%s.amazonaws.com/%s"
                .formatted(bucket, region, key);

        log.debug("[S3] 업로드 완료: {}", url);
        return url;
    }

    @Override
    public void delete(String key) {
        s3Client.deleteObject(
                DeleteObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .build()
        );

        log.debug("[S3] 삭제 완료: {}", key);
    }
}