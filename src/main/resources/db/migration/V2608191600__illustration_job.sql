-- AI 일러스트 생성 작업 (CATCHEAT-87)
-- 생성에 20초 안팎이 걸려 요청 안에서 끝낼 수 없다. 행을 먼저 만들고 상태를 조회한다.

CREATE TABLE illustration_job (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT      NOT NULL,

    -- 프롬프트는 자리로만 갈린다. 소재(음식·인물·사물)는 받지 않는다
    purpose          VARCHAR(30) NOT NULL,
    -- STYLIZE는 사진 변환, GENERATE는 설명만으로 생성. GENERATE는 뱃지만 쓴다
    mode             VARCHAR(20) NOT NULL,

    source_image_key VARCHAR(512),
    description      VARCHAR(200),

    -- 수정은 새 행을 만들고 부모의 원본 사진을 물려받는다.
    -- 생성물을 되먹이면 3~4세대에서 스타일이 무너진다
    parent_job_id    BIGINT,
    revision_depth   SMALLINT    NOT NULL DEFAULT 0,
    instructions     TEXT,

    status           VARCHAR(20) NOT NULL,
    result_image_key VARCHAR(512),
    failure_code     VARCHAR(40),

    created_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_illustration_job_user   FOREIGN KEY (user_id)       REFERENCES users(id),
    CONSTRAINT fk_illustration_job_parent FOREIGN KEY (parent_job_id) REFERENCES illustration_job(id)
);

-- 일일 상한 계산용
CREATE INDEX idx_illustration_job_user_day ON illustration_job (user_id, created_at);
CREATE INDEX idx_illustration_job_parent   ON illustration_job (parent_job_id);
