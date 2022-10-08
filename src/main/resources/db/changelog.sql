-- liquibase formatted sql

-- changeset liquibase:1
CREATE TABLE `user`
(
    `id`           LONG PRIMARY KEY AUTO_INCREMENT NOT NULL,
    `username`     VARCHAR2 UNIQUE                 NOT NULL,
    `password`     BYTEA                           NOT NULL,
    `salt`         BYTEA                           NOT NULL,
    `iv`           BYTEA                           NOT NULL,
    `hash_sum`     BYTEA                           NOT NULL,
    `nonce`        BYTEA                           NOT NULL,
    `created`      DATETIME                        NOT NULL,
    `last_updated` DATETIME                        NOT NULL
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
    FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON UPDATE cascade ON DELETE cascade
)