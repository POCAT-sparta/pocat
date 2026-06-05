
# Card Image S3 Upload on Admin Approval

**Date:** 2026-06-05  
**Status:** Approved

---

## 요약

유저가 카드 신청 시 제출하는 이미지(외부 URL 또는 파일 업로드)를 관리자 승인 시점에 S3 최종 경로로 저장하도록 한다.  
거절 시에는 임시 S3 파일을 삭제해 불필요한 저장소 낭비를 방지한다.

---

## 전체 흐름

### 외부 URL 신청 (기존 엔드포인트 유지)

```
POST /api/v1/cards (JSON)
  → Card 저장 (status=PENDING, imageUrl=외부URL)
```

### 파일 업로드 신청 (신규 엔드포인트)

```
POST /api/v1/cards/upload (multipart/form-data)
  → Card 저장 (status=PENDING, imageUrl=null)
  → S3 업로드: cards/pending/{cardId}/image
  → Card.imageUrl = 임시 S3 URL
```

### 관리자 승인

```
PATCH /api/v1/admin/cards/{cardId}/approve
  → card.getImageUrl()에서 bytes + content-type 다운로드
  → S3 최종 업로드: cards/manual/{cardId}/image
  → imageUrl이 pending 경로("cards/pending/")이면 pending S3 오브젝트 삭제
  → Card.imageUrl = 최종 S3 URL
  → Card.status = ACTIVE
```

### 관리자 거절

```
PATCH /api/v1/admin/cards/{cardId}/reject
  → imageUrl이 pending 경로("cards/pending/")이면 S3 삭제
  → Card.status = REJECTED
```

---

## 컴포넌트 변경

### 1. `S3Uploader` 인터페이스

추가 메서드 및 헬퍼:

```java
void delete(String key);

static String cardPendingImageKey(Long cardId) {
    return "cards/pending/" + cardId + "/image";
}

static String cardManualImageKey(Long cardId) {
    return "cards/manual/" + cardId + "/image";
}
```

기존 `cardImageKey(String tcgdexId)` 는 TCGDex 배치 마이그레이션 전용으로 유지.

### 2. `S3UploaderImpl`

`delete(String key)` 구현: AWS SDK `DeleteObjectRequest` 사용.

### 3. `S3ImageDownloader` (신규 유틸 서비스)

URL에서 이미지 bytes와 content-type을 함께 반환하는 단순 유틸.  
`CardCommandService`와 `CardImageMigrationService` 양쪽에서 재사용.

```java
public record DownloadResult(byte[] bytes, String contentType) {}

DownloadResult download(String imageUrl);
```

- `RestTemplate.exchange(url, GET, null, byte[].class)` 로 응답 헤더에서 `Content-Type` 추출
- 타임아웃: connect 5s, read 15s

### 4. `CardCommandService`

의존성 추가: `S3Uploader`, `S3ImageDownloader`

**`createCardWithImage(Long userId, CreateCardRequest request, MultipartFile image)`**
1. `Card` 저장 (imageUrl=null, status=PENDING) → cardId 확보
2. `s3Uploader.upload(cardPendingImageKey(cardId), image.getBytes(), image.getContentType())`
3. `card.updateImageUrl(s3Url)`

**`approveCard(Long id)` 변경**
1. `card.approve()`
2. `imageUrl`이 null이 아니면:
   - `s3ImageDownloader.download(imageUrl)` → bytes + contentType
   - `s3Uploader.upload(cardManualImageKey(id), bytes, contentType)`
   - `imageUrl`이 `"cards/pending/"` 포함이면 `s3Uploader.delete(pendingKey(imageUrl))` 실행
   - `card.updateImageUrl(finalS3Url)`
3. ES 인덱싱, 임베딩 이벤트 발행 (기존 유지)

**`rejectCard(Long id, String rejectReason)` 변경**
1. `imageUrl`이 pending 경로이면 `s3Uploader.delete(pendingKey(imageUrl))` 실행
2. `card.reject(rejectReason)` (기존 유지)

**pending 경로 판별 및 키 추출 헬퍼 (private)**
```java
private boolean isPendingS3Url(String url) {
    return url != null && url.contains("/cards/pending/");
}

private String pendingKeyFromUrl(String url) {
    // S3 URL 형식: "https://{bucket}.s3.{region}.amazonaws.com/{key}"
    int idx = url.indexOf(".amazonaws.com/");
    return url.substring(idx + ".amazonaws.com/".length());
    // 결과: "cards/pending/{cardId}/image"
}
```

**imageUrl이 null인 경우 (이미지 없이 신청한 카드)**
- 승인 시 S3 업로드 단계를 건너뛰고 status만 ACTIVE로 변경
- 거절 시 S3 삭제 단계 건너뜀 (null 체크로 처리)

### 5. `CardController`

신규 엔드포인트 추가:

```
POST /api/v1/cards/upload
Content-Type: multipart/form-data

@RequestPart("image")   MultipartFile image
@RequestPart("request") @Valid CreateCardRequest request
```

- 기존 rate limit 동일 적용 (`rate:user:card:{userId}`)
- `cardCommandService.createCardWithImage(userId, request, image)` 호출

---

## S3 키 구조 정리

| 경로 | 용도 |
|---|---|
| `cards/{tcgdexId}/high.webp` | TCGDex 배치 마이그레이션 (기존) |
| `cards/pending/{cardId}/image` | 유저 파일 업로드 임시 저장 |
| `cards/manual/{cardId}/image` | 관리자 승인 후 최종 저장 |

---

## 에러 처리

- 이미지 다운로드 실패(네트워크 오류, 404 등): `CardException(CARD_IMAGE_DOWNLOAD_FAILED)` 신규 에러코드 추가, 승인 취소
- S3 업로드 실패: 동일 예외로 승인 취소 (트랜잭션 롤백)
- pending 삭제 실패: 로그 경고만, 승인/거절 결과에 영향 없음 (삭제는 best-effort)

---

## 미변경 범위

- `CardImageMigrationService`: TCGDex CDN → S3 배치 마이그레이션, 별도 운영 유지
- `Card` 엔티티: 컬럼 변경 없음 (`imageUrl` 기존 필드 그대로 사용)
- `CreateCardRequest`: 변경 없음 (파일 업로드는 별도 엔드포인트)
