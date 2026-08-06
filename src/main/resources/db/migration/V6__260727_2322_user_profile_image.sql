-- 프로필 사진: S3 object key를 저장한다. NULL이면 사진 없음(프론트가 닉네임 첫 글자로 대체).
-- 조회 시 S3PresignedUrlService.createDownloadUrl(key)로 표시용 URL을 발급한다.
ALTER TABLE users ADD COLUMN profile_image_key VARCHAR(512);
