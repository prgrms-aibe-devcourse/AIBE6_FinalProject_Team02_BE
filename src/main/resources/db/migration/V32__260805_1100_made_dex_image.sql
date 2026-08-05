-- 제작 도감 표지 이미지(S3 object key) 추가
-- NULL이면 화면이 기본 표지 이미지를 대신 보여준다
ALTER TABLE made_dex
    ADD COLUMN image_key VARCHAR(512);
