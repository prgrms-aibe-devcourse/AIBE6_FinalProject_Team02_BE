# Basic Dex 이미지 S3 조회 플로우 정리

## 1. 현재 담당 범위

Basic Dex 화면에서 사용하는 기본 도감 이미지는 서버가 DB의 `basic_dex` 데이터를 조회한 뒤, 각 도감 칸에 대응되는 S3 객체 경로를 다운로드 가능한 URL로 변환해서 내려주는 방식이다.

현재 버킷은 private 기준이므로, 클라이언트가 S3 객체에 직접 접근하는 공개 URL을 사용하는 구조가 아니라 서버에서 presigned GET URL을 발급해 응답하는 구조로 이해해야 한다.

## 2. API 호출 흐름

클라이언트는 기본 도감 목록을 조회할 때 다음 API를 호출한다.

```http
GET /api/v1/dex/basic
```

컨트롤러 흐름은 다음과 같다.

```java
BasicDexController.getBasicDex()
-> BasicDexService.findAll()
-> BasicDexRepository.findAllByOrderByIdAsc()
-> S3PresignedUrlService.createDownloadUrl(...)
-> BasicDexResponse
```

응답은 공통 API 응답 포맷을 따른다.

```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "name": "김치찌개",
      "category": "국·탕·찌개",
      "illustrationUrl": "https://...presigned-get-url..."
    }
  ]
}
```

## 3. 이미지 경로 결정 방식

`BasicDexService`는 각 `BasicDexEntity`의 이미지 경로를 다음 순서로 결정한다.

1. `basic_dex.illustration_url` 값이 있으면 그 값을 사용한다.
2. 값이 없으면 카테고리 폴더명과 음식명을 조합해 기본 S3 key를 만든다.

기본 key 생성 형식은 다음과 같다.

```text
{카테고리_일러스트_폴더명}/{음식명}.png
```

예시:

```text
국-탕-찌개/김치찌개.png
```

이후 한글 파일명/폴더명 호환을 위해 `Normalizer.normalize(..., Normalizer.Form.NFD)` 처리를 거친다.

## 4. 다운로드 URL 생성 방식

`S3PresignedUrlService.createDownloadUrl()`은 이미지 위치 문자열을 받아 다운로드 URL을 만든다.

처리 방식은 다음과 같다.

1. 값이 `null` 또는 blank이면 `null` 반환
2. 값이 `http://` 또는 `https://`로 시작하면 그대로 반환
3. 그 외에는 S3 object key로 보고 presigned GET URL 생성

private 버킷 기준에서 실제 이미지 조회에 중요한 부분은 3번이다.

```java
GetObjectRequest
-> GetObjectPresignRequest
-> s3Presigner.presignGetObject(...)
```

현재 presigned GET URL 유효시간은 10분이다.

```java
private static final Duration SIGNATURE_DURATION = Duration.ofMinutes(10);
```

## 5. `PresignedUploadResponseDTO`와의 관계

`PresignedUploadResponseDTO`는 Basic Dex 이미지 조회용 DTO가 아니다.

이 DTO는 업로드 URL 발급 API에서만 사용된다.

```http
POST /api/v1/uploads/presigned
```

현재 필드는 다음과 같다.

```java
String key
String uploadUrl
String publicUrl
```

각 필드의 의미는 다음과 같다.

- `key`: S3에 저장될 객체 key
- `uploadUrl`: 클라이언트가 S3에 PUT 업로드할 때 사용하는 presigned URL
- `publicUrl`: `AWS_PUBLIC_BASE_URL + key`로 조합한 URL

private 버킷 기준에서는 `publicUrl`을 이미지 표시용 URL로 사용하면 403 오류가 발생할 수 있다. Basic Dex 이미지 조회는 이 DTO를 사용하지 않고, `BasicDexResponse.illustrationUrl`에 presigned GET URL을 담아 내려준다.

## 6. 현재 구조에서 오류가 날 수 있는 지점

### 6.1 private 버킷인데 public URL을 사용하는 경우

`PresignedUploadResponseDTO.publicUrl`은 이름상 공개 접근 가능한 URL처럼 보이지만, 버킷이 private이면 실제 접근이 막힐 수 있다.

따라서 프론트에서 업로드 직후 이미지 표시를 위해 `publicUrl`을 사용하면 403 오류가 발생할 수 있다. private 버킷에서는 업로드 완료 후 서버에 `key`를 저장하고, 조회 시 별도의 presigned GET URL을 받아 표시하는 흐름이 맞다.

### 6.2 Basic Dex 전체 조회 시 URL을 매번 200개 생성하는 문제

기본 도감은 200칸 고정이므로 `/api/v1/dex/basic` 호출 한 번에 최대 200개의 presigned GET URL을 생성할 수 있다.

이 방식은 구현은 단순하지만 다음 문제가 생길 수 있다.

- API 응답 생성 시간이 길어질 수 있다.
- 같은 이미지에 대해서도 요청마다 새로운 presigned URL이 만들어진다.
- 프론트가 200개 이미지를 한 번에 요청하면 초기 로딩이 느려질 수 있다.
- URL 만료 시간이 짧아 클라이언트 캐시 효율이 낮다.

### 6.3 S3 key의 한글 정규화 문제

현재 기본 이미지 key는 `Normalizer.Form.NFD`로 정규화한다.

S3에 업로드된 실제 object key가 NFC 방식이고, 서버에서 요청하는 key가 NFD 방식이면 같은 글자로 보여도 다른 key로 인식되어 `NoSuchKey` 오류가 날 수 있다.

따라서 S3에 저장된 key의 정규화 방식과 서버에서 생성하는 key의 정규화 방식이 반드시 일치해야 한다.

### 6.4 `illustration_url`에 URL과 key가 섞일 수 있는 문제

`createDownloadUrl()`은 `http://` 또는 `https://`로 시작하면 그대로 반환하고, 아니면 S3 key로 처리한다.

즉 `basic_dex.illustration_url`에는 다음 값들이 섞여 들어갈 수 있다.

- 완전한 URL
- S3 key
- `s3://bucket/key`

동작은 가능하지만 데이터 저장 규칙이 명확하지 않으면 운영 중 혼동이 생길 수 있다.

## 7. 개선 방향

### 7.1 업로드 응답 DTO 개선

private 버킷 기준에서는 `PresignedUploadResponseDTO`에서 `publicUrl`을 제거하거나 이름을 변경하는 것이 좋다.

추천 형태:

```java
public record PresignedUploadResponseDTO(
        List<UploadTarget> uploads
) {
    public record UploadTarget(
            String key,
            String uploadUrl,
            Instant expiresAt
    ) {}
}
```

업로드 응답은 "업로드에 필요한 정보"만 제공하고, 이미지 표시는 다운로드용 presigned GET URL을 별도로 받도록 분리하는 것이 안전하다.

### 7.2 Basic Dex 다운로드 URL 캐싱

Basic Dex 이미지는 운영진이 관리하는 정적 리소스에 가깝기 때문에 매 요청마다 presigned GET URL을 새로 만들 필요가 적다.

개선안:

- `key -> presignedGetUrl` 캐시 적용
- 캐시 TTL은 presigned URL 만료시간보다 짧게 설정
- 현재 만료시간이 10분이면 캐시 TTL은 8~9분 정도가 적절

### 7.3 Basic Dex 이미지와 사용자 업로드 사진 정책 분리

추천 정책:

- Basic Dex 기본 일러스트: 가능하면 CDN 또는 CloudFront 기반 제공
- 사용자 업로드 사진: private S3 + presigned GET URL

Basic Dex 이미지까지 private로 유지해야 한다면 presigned GET URL을 사용하되, 캐싱과 lazy loading을 같이 적용하는 것이 좋다.

### 7.4 프론트 이미지 로딩 최적화

서버 개선과 별개로 프론트에서도 다음 최적화가 필요하다.

- 화면에 보이는 이미지만 우선 로드
- `loading="lazy"` 적용
- 도감 그리드에는 썸네일 사용
- 상세 화면에서만 원본 또는 큰 이미지 사용

## 8. 정리

현재 Basic Dex 이미지 조회는 `PresignedUploadResponseDTO`를 사용하지 않는다. Basic Dex는 `BasicDexResponse.illustrationUrl`에 `S3PresignedUrlService.createDownloadUrl()`로 만든 presigned GET URL을 담아 내려주는 구조다.

private 버킷 기준에서 가장 주의해야 할 점은 업로드 응답의 `publicUrl`을 이미지 표시용으로 사용하지 않는 것이다. 업로드는 `key`와 `uploadUrl`, 조회는 presigned GET URL로 역할을 분리하는 것이 안전하다.
