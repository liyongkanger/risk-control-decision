CREATE TABLE scene (
    scene_code VARCHAR(64) PRIMARY KEY,
    published_version_id BIGINT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE flow_draft (
    scene_code VARCHAR(64) PRIMARY KEY,
    graph_json LONGTEXT NOT NULL,
    revision BIGINT NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE flow_version (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    scene_code VARCHAR(64) NOT NULL,
    version_number BIGINT NOT NULL,
    graph_json LONGTEXT NOT NULL,
    chain_id VARCHAR(128) NOT NULL,
    compiled_el TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    UNIQUE (scene_code, version_number),
    UNIQUE (chain_id)
);
