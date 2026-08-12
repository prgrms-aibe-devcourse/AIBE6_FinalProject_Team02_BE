-- 로그잇 사진 크롭 위치: 정사각형 프레임에서 사진의 초점 위치를 저장한다
-- 값은 0~100 퍼센트. 기본값 50은 가운데 정렬(object-position: 50% 50%)

ALTER TABLE made_dex_record_photo ADD COLUMN crop_x DOUBLE PRECISION NOT NULL DEFAULT 50;
ALTER TABLE made_dex_record_photo ADD COLUMN crop_y DOUBLE PRECISION NOT NULL DEFAULT 50;
