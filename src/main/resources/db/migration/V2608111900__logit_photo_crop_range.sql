-- 로그잇 사진 크롭 좌표의 범위를 DB에서도 강제한다 (CATCHEAT-53, 코드래빗 리뷰)
-- crop_x·crop_y는 objectPosition의 백분율이라 0~100 밖의 값은 화면에서 의미가 없다.
--
-- 서비스가 clampCrop으로, 엔티티가 생성·수정 시점에 한 번 더 좁혀서 범위 밖 값은
-- 여기까지 오지 못한다. 이 제약은 그 두 겹이 뚫렸을 때를 위한 마지막 그물이다.
--
-- 컬럼을 추가한 V2608101030에 함께 넣지 않은 이유는 그 마이그레이션이 이미 적용되고
-- 푸시됐기 때문이다. 적용된 파일을 고치면 체크섬이 어긋나 팀 전원의 기동이 막힌다.
-- 번호도 V2608111800보다 뒤여야 한다 — dev는 out-of-order를 켜지 않아 앞 번호는 거부된다.

ALTER TABLE made_dex_record_photo
    ADD CONSTRAINT ck_made_dex_record_photo_crop_x CHECK (crop_x >= 0 AND crop_x <= 100),
    ADD CONSTRAINT ck_made_dex_record_photo_crop_y CHECK (crop_y >= 0 AND crop_y <= 100);
