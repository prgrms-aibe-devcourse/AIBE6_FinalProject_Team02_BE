-- 로그잇 식사 기록: 올린 시각 + 사진별 캡션 + 업로더 검증 + 비공개 전용 전환 (CATCHEAT-52)

-- ── 먹은 시각 ──────────────────────────────────────────────
-- 사용자가 직접 적을 때만 채운다. 비워 두면 화면에도 시각을 띄우지 않는다.
-- created_at을 대신 쓰지 않는 이유는 타임존 없는 TIMESTAMP라 배포(UTC)와 로컬(KST)이 다르기 때문이다.
ALTER TABLE made_dex_record ADD COLUMN logged_at TIMESTAMP;

-- ── 사진별 캡션 ────────────────────────────────────────────
-- 기록 전체 메모(made_dex_record.memo)를 대신한다.
-- memo 컬럼은 지우지 않는다. 이미 남긴 글이 있고, 컬럼 정리는 데이터가 정리된 뒤에 한다.
ALTER TABLE made_dex_record_photo ADD COLUMN caption VARCHAR(100);

-- ── 업로드 객체 소유자 ──────────────────────────────────────
-- presign을 받은 사람을 남겨, 남의 key를 자기 기록에 붙이지 못하게 한다.
-- 이 표에 없는 key는 이 마이그레이션 이전에 발급된 것이라 통과시킨다.
CREATE TABLE upload_object (
    image_key VARCHAR(512) PRIMARY KEY,
    uploader_id BIGINT NOT NULL,
    purpose VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_upload_object_uploader FOREIGN KEY (uploader_id) REFERENCES users(id)
);

CREATE INDEX idx_upload_object_uploader ON upload_object (uploader_id);

-- ── 로그잇은 비공개 전용 ────────────────────────────────────
-- 값만 바꾸면 아직 PUBLIC을 받는 API 호출 한 번에 공개 그룹이 다시 생긴다.
-- 컬럼과 코드 정리는 후속 이슈에서 한다.
UPDATE made_dex SET visibility = 'PRIVATE' WHERE visibility <> 'PRIVATE';

ALTER TABLE made_dex
    ADD CONSTRAINT ck_made_dex_visibility_private CHECK (visibility = 'PRIVATE');
