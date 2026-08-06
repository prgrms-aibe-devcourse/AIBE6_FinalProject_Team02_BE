-- ===== auth 도메인 =====
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    provider VARCHAR(20) NOT NULL,
    provider_id VARCHAR(255) NOT NULL,
    nickname VARCHAR(50) NOT NULL UNIQUE,
    nickname_updated_at TIMESTAMP,
    onboarding_completed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT uk_user_provider UNIQUE (provider, provider_id)
);

-- ===== dex 도메인 =====
CREATE TABLE basic_dex (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    category VARCHAR(50) NOT NULL,
    illustration_url VARCHAR(500)
);

CREATE INDEX idx_basic_dex_category ON basic_dex (category);

CREATE TABLE user_collection (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    slot_id BIGINT NOT NULL,
    rank INT NOT NULL DEFAULT 1,
    first_collected_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_user_collection_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_user_collection_slot FOREIGN KEY (slot_id) REFERENCES basic_dex(id),
    CONSTRAINT uk_user_slot UNIQUE (user_id, slot_id)
);

-- ===== registration 도메인 =====
CREATE TABLE registration (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_registration_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE photo (
    id BIGSERIAL PRIMARY KEY,
    registration_id BIGINT NOT NULL,
    url VARCHAR(500) NOT NULL,
    hash VARCHAR(255) NOT NULL UNIQUE,
    CONSTRAINT fk_photo_registration FOREIGN KEY (registration_id) REFERENCES registration(id)
);

CREATE TABLE food_candidate (
    id BIGSERIAL PRIMARY KEY,
    registration_id BIGINT NOT NULL,
    slot_id BIGINT NOT NULL,
    confidence DOUBLE PRECISION NOT NULL,
    selected BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_food_candidate_registration FOREIGN KEY (registration_id) REFERENCES registration(id),
    CONSTRAINT fk_food_candidate_slot FOREIGN KEY (slot_id) REFERENCES basic_dex(id)
);

CREATE TABLE memo_template (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    content VARCHAR(100) NOT NULL,
    CONSTRAINT fk_memo_template_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE unidentified_food_report (
    id BIGSERIAL PRIMARY KEY,
    registration_id BIGINT NOT NULL,
    description VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    CONSTRAINT fk_unidentified_food_report_registration FOREIGN KEY (registration_id) REFERENCES registration(id)
);

-- ===== dex 도메인 (Registration 참조로 인해 아래 배치) =====
CREATE TABLE collection_card (
    id BIGSERIAL PRIMARY KEY,
    registration_id BIGINT NOT NULL,
    user_collection_id BIGINT NOT NULL,
    representative_photo_id BIGINT NOT NULL,
    memo VARCHAR(100),
    location_name VARCHAR(255),
    lat DOUBLE PRECISION,
    lng DOUBLE PRECISION,
    verification_status VARCHAR(20) NOT NULL,
    collected_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_collection_card_registration FOREIGN KEY (registration_id) REFERENCES registration(id),
    CONSTRAINT fk_collection_card_user_collection FOREIGN KEY (user_collection_id) REFERENCES user_collection(id),
    CONSTRAINT fk_collection_card_representative_photo FOREIGN KEY (representative_photo_id) REFERENCES photo(id)
);

-- ===== user 도메인 (뱃지) =====
CREATE TABLE badge (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    image_url VARCHAR(500)
);

CREATE TABLE user_badge (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    badge_id BIGINT NOT NULL,
    acquired_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_badge_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_user_badge_badge FOREIGN KEY (badge_id) REFERENCES badge(id),
    CONSTRAINT uk_user_badge UNIQUE (user_id, badge_id)
);

-- ===== admin 도메인 =====
CREATE TABLE review_queue_item (
    id BIGSERIAL PRIMARY KEY,
    registration_id BIGINT NOT NULL,
    evidence_photo_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    CONSTRAINT fk_review_queue_item_registration FOREIGN KEY (registration_id) REFERENCES registration(id),
    CONSTRAINT fk_review_queue_item_evidence_photo FOREIGN KEY (evidence_photo_id) REFERENCES photo(id)
);
