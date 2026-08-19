ALTER TABLE made_dex_record ADD COLUMN like_count INT NOT NULL DEFAULT 0;

CREATE TABLE made_dex_record_like (
    id BIGSERIAL PRIMARY KEY,
    record_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT uq_made_dex_record_like UNIQUE (record_id, user_id),
    CONSTRAINT fk_made_dex_record_like_record FOREIGN KEY (record_id) REFERENCES made_dex_record(id),
    CONSTRAINT fk_made_dex_record_like_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_made_dex_record_like_record ON made_dex_record_like (record_id);
