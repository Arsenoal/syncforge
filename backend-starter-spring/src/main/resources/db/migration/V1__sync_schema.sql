CREATE TABLE sync_entity (
    entity_type VARCHAR(255) NOT NULL,
    entity_id VARCHAR(255) NOT NULL,
    payload_json TEXT,
    server_version BIGINT NOT NULL,
    updated_at_millis BIGINT NOT NULL,
    is_deleted BOOLEAN NOT NULL,
    PRIMARY KEY (entity_type, entity_id)
);

CREATE TABLE sync_version_counter (
    id INT PRIMARY KEY,
    next_version BIGINT NOT NULL
);

INSERT INTO sync_version_counter (id, next_version) VALUES (1, 1);