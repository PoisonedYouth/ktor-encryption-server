-- liquibase formatted sql

-- changeset liquibase:1
CREATE TABLE app_user_settings
(
    id                          SERIAL PRIMARY KEY NOT NULL,
    upload_file_expiration_days BIGINT             NOT NULL,
    created                     TIMESTAMP          NOT NULL,
    last_updated                TIMESTAMP          NOT NULL
);
CREATE TABLE security_settings
(
    id                                SERIAL PRIMARY KEY NOT NULL,
    integrity_check_hashing_algorithm TEXT               NOT NULL,
    password_key_size                 INT                NOT NULL,
    nonce_length                      INT                NOT NULL,
    salt_length                       INT                NOT NULL,
    iteration_count                   INT                NOT NULL,
    gcm_parameter_spec_length         INT                NOT NULL
);
CREATE TABLE app_user
(
    id                   SERIAL PRIMARY KEY NOT NULL,
    username             TEXT UNIQUE        NOT NULL,
    password             BYTEA              NOT NULL,
    salt                 BYTEA              NOT NULL,
    iv                   BYTEA              NOT NULL,
    hash_sum             BYTEA              NOT NULL,
    nonce                BYTEA              NOT NULL,
    created              TIMESTAMP          NOT NULL,
    last_updated         TIMESTAMP          NOT NULL,
    user_settings_id     BIGINT             NOT NULL,
    security_settings_id BIGINT             NOT NULL,
    FOREIGN KEY (security_settings_id) REFERENCES security_settings (id) ON UPDATE cascade ON DELETE CASCADE,
    FOREIGN KEY (user_settings_id) REFERENCES app_user_settings (id) ON UPDATE cascade ON DELETE RESTRICT

);
CREATE TABLE upload_file
(
    id                 SERIAL PRIMARY KEY NOT NULL,
    filename           TEXT               NOT NULL,
    encrypted_filename TEXT UNIQUE        NOT NULL,
    salt               BYTEA              NOT NULL,
    hash_sum           BYTEA              NOT NULL,
    created            TIMESTAMP          NOT NULL,
    iv                 BYTEA              NOT NULL,
    nonce              BYTEA              NOT NULL,
    user_id            BIGINT             NOT NULL,
    settings_id        BIGINT             NOT NULL,
    FOREIGN KEY (user_id) REFERENCES app_user (id) ON UPDATE cascade ON DELETE cascade,
    FOREIGN KEY (settings_id) REFERENCES security_settings (id) ON UPDATE cascade ON DELETE cascade
);
CREATE TABLE upload_file_history
(
    id             SERIAL PRIMARY KEY NOT NULL,
    ip_address     TEXT               NOT NULL,
    created        TIMESTAMP          NOT NULL,
    action         TEXT               NOT NULL,
    upload_file_id BIGINT             NOT NULL,
    FOREIGN KEY (upload_file_id) REFERENCES upload_file (id) ON UPDATE cascade ON DELETE cascade
);

-- changeset liquibase:2
ALTER TABLE upload_file ADD COLUMN mime_type TEXT NOT NULL DEFAULT '*'