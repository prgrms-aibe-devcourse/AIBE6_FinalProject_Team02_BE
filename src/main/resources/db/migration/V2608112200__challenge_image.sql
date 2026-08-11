-- 챌린지 대표 이미지 (S3 object key). 프로필 사진과 동일 방식(조회 시 프리사인 URL 변환)
ALTER TABLE challenge_dex ADD COLUMN image_key VARCHAR(512);
