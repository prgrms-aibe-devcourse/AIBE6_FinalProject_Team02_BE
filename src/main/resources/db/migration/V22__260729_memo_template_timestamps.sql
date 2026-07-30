-- 메모 템플릿을 "최근 사용순"으로 정렬하려면 사용 시각이 필요하다 (PLAN 메모 템플릿 절).
-- created_at/updated_at은 BaseEntity가 요구하는 감사 컬럼이다.
ALTER TABLE memo_template
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_at TIMESTAMP,
    ADD COLUMN last_used_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- 목록은 항상 "내 것만, 최근 사용순"으로 읽는다
CREATE INDEX idx_memo_template_user_last_used
    ON memo_template (user_id, last_used_at DESC);
