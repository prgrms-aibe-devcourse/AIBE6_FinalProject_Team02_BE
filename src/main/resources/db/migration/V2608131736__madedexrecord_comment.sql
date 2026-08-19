
CREATE TABLE made_dex_comment (
    id BIGSERIAL PRIMARY KEY,
    made_dex_record_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    content VARCHAR(500) NOT NULL,
    like_count INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT fk_made_dex_comment_record FOREIGN KEY (made_dex_record_id) REFERENCES made_dex_record(id),
    CONSTRAINT fk_made_dex_comment_author FOREIGN KEY (author_id) REFERENCES users(id)
);

CREATE INDEX idx_made_dex_comment_record ON made_dex_comment (made_dex_record_id);

CREATE TABLE made_dex_comment_like (
    id BIGSERIAL PRIMARY KEY,
    comment_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT uq_made_dex_comment_like UNIQUE (comment_id, user_id),
    CONSTRAINT fk_made_dex_comment_like_comment FOREIGN KEY (comment_id) REFERENCES made_dex_comment(id),
    CONSTRAINT fk_made_dex_comment_like_user FOREIGN KEY (user_id) REFERENCES users(id)
);