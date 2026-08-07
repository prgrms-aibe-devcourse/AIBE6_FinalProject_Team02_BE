-- 양방향 친구. 단일 행 + 상태(PENDING/ACCEPTED). 친구=ACCEPTED, 방향은 requester/addressee로 구분
CREATE TABLE friendship (
    id BIGSERIAL PRIMARY KEY,
    requester_id BIGINT NOT NULL,   -- 요청 보낸 사람
    addressee_id BIGINT NOT NULL,   -- 요청 받은 사람
    status VARCHAR(20) NOT NULL,    -- PENDING | ACCEPTED
    responded_at TIMESTAMP,         -- 수락 시각
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT fk_friendship_requester FOREIGN KEY (requester_id) REFERENCES users (id),
    CONSTRAINT fk_friendship_addressee FOREIGN KEY (addressee_id) REFERENCES users (id),
    CONSTRAINT ck_friendship_not_self CHECK (requester_id <> addressee_id)
);

-- 방향 무관 유일성. (A,B)와 (B,A)를 같은 쌍으로 취급 → 중복 요청·역방향 동시 요청 원천 차단
CREATE UNIQUE INDEX uk_friendship_pair
    ON friendship (LEAST(requester_id, addressee_id), GREATEST(requester_id, addressee_id));

CREATE INDEX idx_friendship_addressee_status ON friendship (addressee_id, status);
CREATE INDEX idx_friendship_requester_status ON friendship (requester_id, status);
