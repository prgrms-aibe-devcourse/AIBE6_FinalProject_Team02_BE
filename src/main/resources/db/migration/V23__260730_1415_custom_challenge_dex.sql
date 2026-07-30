-- ============================================================
-- 1차 확장: 제작 도감(made_dex) / 챌린지·이벤트 도감(challenge_dex) 기본 스키마
-- 기존 컨벤션 준수: 단수 snake_case, BaseEntity(created_at/updated_at), FK 명시
-- 음식 기록은 basic_dex 슬롯에 묶지 않고 자유 입력(food_name)으로 둔다
-- ============================================================


-- ======================== 제작 도감 (지인 그룹형) ========================

-- 그룹(제작 도감) 본체. 개설자(그룹장)가 생성, 공개/비공개 언제든 변경 가능
CREATE TABLE made_dex (
    id BIGSERIAL PRIMARY KEY,
    owner_id BIGINT NOT NULL,                         -- 개설자(그룹장)
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',-- PUBLIC / PRIVATE
    max_members INT NOT NULL DEFAULT 12,              -- 그룹 최대 인원 12명
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,                             -- soft delete
    CONSTRAINT fk_made_dex_owner FOREIGN KEY (owner_id) REFERENCES users(id)
);
CREATE INDEX idx_made_dex_owner ON made_dex (owner_id);

-- 그룹 멤버. 개설자도 OWNER 로 한 행 가진다. 1인당 가입 그룹 수 무제한
CREATE TABLE made_dex_member (
    id BIGSERIAL PRIMARY KEY,
    made_dex_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'MEMBER',       -- OWNER / MEMBER
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_made_dex_member_dex FOREIGN KEY (made_dex_id) REFERENCES made_dex(id) ON DELETE CASCADE,
    CONSTRAINT fk_made_dex_member_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uk_made_dex_member UNIQUE (made_dex_id, user_id)  -- 같은 그룹 중복 가입 방지
);
CREATE INDEX idx_made_dex_member_user ON made_dex_member (user_id);

-- 초대 코드/링크. 코드 하나로 만료 전까지 여러 명 합류 가능(단톡방 초대링크 느낌)
CREATE TABLE made_dex_invite (
    id BIGSERIAL PRIMARY KEY,
    made_dex_id BIGINT NOT NULL,
    code VARCHAR(32) NOT NULL,
    created_by BIGINT NOT NULL,
    expires_at TIMESTAMP NOT NULL,                    -- 발급 후 7일 만료(생성 시 계산)
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_made_dex_invite_dex FOREIGN KEY (made_dex_id) REFERENCES made_dex(id) ON DELETE CASCADE,
    CONSTRAINT fk_made_dex_invite_creator FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT uk_made_dex_invite_code UNIQUE (code)
);
CREATE INDEX idx_made_dex_invite_dex ON made_dex_invite (made_dex_id);

-- 그룹 도감 기록(자유 추가형). 멤버 누구나 음식 사진 + 이름으로 기록
CREATE TABLE made_dex_entry (
    id BIGSERIAL PRIMARY KEY,
    made_dex_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,                        -- 작성 멤버
    food_name VARCHAR(100) NOT NULL,                  -- 자유 입력(basic_dex 비참조)
    image_key VARCHAR(512),                           -- S3 object key
    memo VARCHAR(100),
    location_name VARCHAR(255),
    lat DOUBLE PRECISION,
    lng DOUBLE PRECISION,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT fk_made_dex_entry_dex FOREIGN KEY (made_dex_id) REFERENCES made_dex(id) ON DELETE CASCADE,
    CONSTRAINT fk_made_dex_entry_author FOREIGN KEY (author_id) REFERENCES users(id)
);
CREATE INDEX idx_made_dex_entry_dex ON made_dex_entry (made_dex_id);
CREATE INDEX idx_made_dex_entry_author ON made_dex_entry (author_id);


-- ==================== 챌린지 도감 / 이벤트 도감 ====================
-- 이벤트 도감 = is_event=TRUE (운영진 개설). 나머지 구조는 챌린지와 동일

-- 개설권. 챌린지 개설 시 1장 소모. used_at IS NULL 이면 미사용(마이페이지 보유 개수)
CREATE TABLE challenge_ticket (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    challenge_dex_id BIGINT,                          -- 소모된 챌린지(미사용이면 NULL)
    used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_challenge_ticket_user FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE INDEX idx_challenge_ticket_user_unused ON challenge_ticket (user_id) WHERE used_at IS NULL;

-- 챌린지 도감 본체. 전부 public. 기한/유형은 개최 후 변경 불가(앱에서 검증)
CREATE TABLE challenge_dex (
    id BIGSERIAL PRIMARY KEY,
    owner_id BIGINT NOT NULL,                         -- 개설자(이벤트면 운영진)
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    challenge_type VARCHAR(20) NOT NULL,              -- FIRST_COME(선착순) / COLLECTION(수집형)
    period_type VARCHAR(20) NOT NULL,                 -- PERMANENT(상시) / LIMITED(기간한정)
    starts_at TIMESTAMP NOT NULL,
    ends_at TIMESTAMP,                                -- PERMANENT 이면 NULL
    reward_badge_id BIGINT,                           -- 완료 보상 뱃지(개설 시 badge 생성 후 연결)
    is_event BOOLEAN NOT NULL DEFAULT FALSE,          -- TRUE = 운영진 이벤트 도감(제철 등)
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_challenge_dex_owner FOREIGN KEY (owner_id) REFERENCES users(id),
    CONSTRAINT fk_challenge_dex_reward_badge FOREIGN KEY (reward_badge_id)
        REFERENCES badge(id) ON DELETE SET NULL,
    CONSTRAINT ck_challenge_type CHECK (challenge_type IN ('FIRST_COME', 'COLLECTION')),
    CONSTRAINT ck_challenge_period CHECK (period_type IN ('PERMANENT', 'LIMITED'))
);
CREATE INDEX idx_challenge_dex_owner ON challenge_dex (owner_id);
CREATE INDEX idx_challenge_dex_event ON challenge_dex (is_event);
CREATE INDEX idx_challenge_dex_ends_at ON challenge_dex (ends_at);   -- 진행중/완료 탭 필터

-- 일자별 조회수 버킷. 도감 랭킹의 "조회수" 기준은 최근 N일 합산으로 낸다
-- 단일 카운터로는 기간 집계가 안 되므로 챌린지×날짜당 1행(조회 시 그날 행 +1) 구조
CREATE TABLE challenge_view_daily (
    id BIGSERIAL PRIMARY KEY,
    challenge_dex_id BIGINT NOT NULL,
    view_date DATE NOT NULL,
    view_count BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_challenge_view_daily_dex FOREIGN KEY (challenge_dex_id)
        REFERENCES challenge_dex(id) ON DELETE CASCADE,
    CONSTRAINT uk_challenge_view_daily UNIQUE (challenge_dex_id, view_date)  -- UPSERT 증분용
);
CREATE INDEX idx_challenge_view_daily_date ON challenge_view_daily (view_date);  -- 최근 N일 필터

-- 목표 슬롯(고정 목록형). 개설자가 미리 짜둠, 최소 5개는 앱에서 검증. 음식 + (선택)장소
CREATE TABLE challenge_dex_slot (
    id BIGSERIAL PRIMARY KEY,
    challenge_dex_id BIGINT NOT NULL,
    food_name VARCHAR(100) NOT NULL,
    place_name VARCHAR(255),                          -- 장소 지정 챌린지용(없으면 음식만)
    lat DOUBLE PRECISION,
    lng DOUBLE PRECISION,
    slot_order INT NOT NULL DEFAULT 0,                -- 목록 표시 순서
    CONSTRAINT fk_challenge_dex_slot_dex FOREIGN KEY (challenge_dex_id)
        REFERENCES challenge_dex(id) ON DELETE CASCADE
);
CREATE INDEX idx_challenge_dex_slot_dex ON challenge_dex_slot (challenge_dex_id);

-- 참여자. 자유 참여. completed_at 기록 → 선착순 랭킹은 이 순서로 고정
CREATE TABLE challenge_participant (
    id BIGSERIAL PRIMARY KEY,
    challenge_dex_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,                           -- 완료 시각(미완료면 NULL)
    CONSTRAINT fk_challenge_participant_dex FOREIGN KEY (challenge_dex_id)
        REFERENCES challenge_dex(id) ON DELETE CASCADE,
    CONSTRAINT fk_challenge_participant_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uk_challenge_participant UNIQUE (challenge_dex_id, user_id)
);
CREATE INDEX idx_challenge_participant_user ON challenge_participant (user_id);

-- 참여자 슬롯 해금 기록. 개수 총합이 수집형 랭킹 / 도감 랭킹 기준
CREATE TABLE challenge_unlock (
    id BIGSERIAL PRIMARY KEY,
    challenge_participant_id BIGINT NOT NULL,
    slot_id BIGINT NOT NULL,
    image_key VARCHAR(512),                           -- 인증 사진 S3 key
    unlocked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_challenge_unlock_participant FOREIGN KEY (challenge_participant_id)
        REFERENCES challenge_participant(id) ON DELETE CASCADE,
    CONSTRAINT fk_challenge_unlock_slot FOREIGN KEY (slot_id)
        REFERENCES challenge_dex_slot(id) ON DELETE CASCADE,
    CONSTRAINT uk_challenge_unlock UNIQUE (challenge_participant_id, slot_id)  -- 슬롯 중복 해금 방지
);
CREATE INDEX idx_challenge_unlock_participant ON challenge_unlock (challenge_participant_id);

-- 개설권이 소모한 챌린지 참조 FK는 challenge_dex 생성 뒤에 건다
ALTER TABLE challenge_ticket
    ADD CONSTRAINT fk_challenge_ticket_dex FOREIGN KEY (challenge_dex_id)
        REFERENCES challenge_dex(id) ON DELETE SET NULL;
