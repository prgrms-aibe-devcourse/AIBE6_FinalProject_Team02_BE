-- 로그잇 정리: 음식명·위치·메모·공개 여부 제거 (CATCHEAT-53)
-- V37이 "컬럼과 코드 정리는 후속 이슈에서 한다"고 미뤄 둔 것을 여기서 끝낸다.

-- ── 음식명 ──────────────────────────────────────────────
-- 기록 화면에서 음식명 입력을 없앴다. 신규 행이 쌓이지 않고 읽는 화면도 없다.
-- 딸린 인덱스(record, name)도 테이블과 함께 사라진다.
DROP TABLE IF EXISTS made_dex_record_food;

-- ── 위치 ────────────────────────────────────────────────
-- 로그잇은 장소를 묻지 않는다. 입력 화면이 없어 전부 NULL로만 쌓였다.
ALTER TABLE made_dex_record
    DROP COLUMN IF EXISTS location_name,
    DROP COLUMN IF EXISTS lat,
    DROP COLUMN IF EXISTS lng;

-- ── 메모 ────────────────────────────────────────────────
-- V37이 사진별 캡션(made_dex_record_photo.caption)으로 옮기고 컬럼만 남겨 뒀다.
-- 남아 있는 값은 버린다.
ALTER TABLE made_dex_record DROP COLUMN IF EXISTS memo;

-- ── 공개 여부 ────────────────────────────────────────────
-- 로그잇은 비공개 전용이다. V37이 값을 PRIVATE로 고정하고 CHECK로 묶어 뒀는데,
-- 이제 선택 화면도 코드도 사라져 컬럼 자체가 필요 없다.
-- 컬럼을 지우면 딸린 CHECK도 같이 사라지지만, 이름을 남겨 둬야 추적이 된다.
ALTER TABLE made_dex DROP CONSTRAINT IF EXISTS ck_made_dex_visibility_private;
ALTER TABLE made_dex DROP COLUMN IF EXISTS visibility;
