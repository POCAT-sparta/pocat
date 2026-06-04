package com.rocketcrew.pocat.global.infra.s3;

/**
 * S3 파일 업로드 인터페이스.
 * 실제 S3 구현체와 테스트용 Fake 구현체를 교체할 수 있도록 추상화한다.
 */
public interface S3Uploader {

    /**
     * 바이트 배열을 S3에 업로드하고 접근 가능한 URL을 반환한다.
     *
     * @param key         S3 객체 키 (예: "cards/swsh1-1/high.webp")
     * @param data        업로드할 파일 바이트
     * @param contentType MIME 타입 (예: "image/webp")
     * @return 업로드된 파일의 공개 URL
     */
    String upload(String key, byte[] data, String contentType);
}
