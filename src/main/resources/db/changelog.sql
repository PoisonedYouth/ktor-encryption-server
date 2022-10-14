-- liquibase formatted sql

-- changeset liquibase:1
CREATE TABLE `app_user_settings`
(
    `id`                          LONG PRIMARY KEY AUTO_INCREMENT NOT NULL,
    `upload_file_expiration_days` LONG                            NOT NULL,
    `created`                     DATETIME                        NOT NULL,
    `last_updated`                DATETIME                        NOT NULL
);
CREATE TABLE `app_user`
(
    `id`               LONG PRIMARY KEY AUTO_INCREMENT NOT NULL,
    `username`         VARCHAR2 UNIQUE                 NOT NULL,
    `password`         BYTEA                           NOT NULL,
    `salt`             BYTEA                           NOT NULL,
    `iv`               BYTEA                           NOT NULL,
    `hash_sum`         BYTEA                           NOT NULL,
    `nonce`            BYTEA                           NOT NULL,
    `created`          DATETIME                        NOT NULL,
    `last_updated`     DATETIME                        NOT NULL,
    `user_settings_id` LONG                            NOT NULL,
    FOREIGN KEY (`user_settings_id`) REFERENCES `app_user_settings` (`id`) ON UPDATE cascade ON DELETE RESTRICT

);
CREATE TABLE `upload_file`
(
    `id`                 LONG PRIMARY KEY AUTO_INCREMENT NOT NULL,
    `filename`           VARCHAR2                        NOT NULL,
    `encrypted_filename` VARCHAR2 UNIQUE                 NOT NULL,
    `salt`               BYTEA                           NOT NULL,
    `hash_sum`           BYTEA                           NOT NULL,
    `created`            DATETIME                        NOT NULL,
    `iv`                 BYTEA                           NOT NULL,
    `nonce`              BYTEA                           NOT NULL,
    `user_id`            LONG                            NOT NULL,
    FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`) ON UPDATE cascade ON DELETE cascade
);
CREATE TABLE `upload_file_history`
(
    `id`             LONG PRIMARY KEY AUTO_INCREMENT NOT NULL,
    `ip_address`     VARCHAR2                        NOT NULL,
    `created`        DATETIME                        NOT NULL,
    `action`         VARCHAR2                        NOT NULL,
    `upload_file_id` LONG                            NOT NULL,
    FOREIGN KEY (`upload_file_id`) REFERENCES `upload_file` (`id`) ON UPDATE cascade ON DELETE cascade
);