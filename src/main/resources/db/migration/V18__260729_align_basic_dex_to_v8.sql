CREATE TABLE IF NOT EXISTS basic_dex_alias (
    id BIGSERIAL PRIMARY KEY,
    basic_dex_id BIGINT NOT NULL,
    alias VARCHAR(100) NOT NULL,
    CONSTRAINT fk_basic_dex_alias_basic_dex FOREIGN KEY (basic_dex_id)
        REFERENCES basic_dex (id) ON DELETE CASCADE,
    CONSTRAINT uk_basic_dex_alias UNIQUE (basic_dex_id, alias)
);

CREATE INDEX IF NOT EXISTS idx_basic_dex_alias_alias ON basic_dex_alias (alias);

UPDATE basic_dex
SET name = '아이스크림',
    illustration_url = '디저트/아이스크림, 젤라또.png'
WHERE name IN ('아이스크림', '아이스크림·젤라또');
