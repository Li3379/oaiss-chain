-- =============================================
-- 双碳链动系统数据库初始化
-- OAISS Chain Database Schema
-- 基于 JPA Entity 定义生成，21张表
-- MySQL 8.0+
-- =============================================

SET NAMES utf8mb4;

-- 1. 用户表
CREATE TABLE `user` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `created_at`      DATETIME     NOT NULL,
    `updated_at`      DATETIME     NOT NULL,
    `is_deleted`      TINYINT(1)   NOT NULL DEFAULT 0,
    `username`        VARCHAR(50)  NOT NULL,
    `password`        VARCHAR(255) NOT NULL,
    `phone`           VARCHAR(20)  NULL,
    `email`           VARCHAR(100) NULL,
    `real_name`       VARCHAR(50)  NULL,
    `user_type`       INT          NOT NULL,
    `status`          INT          NOT NULL DEFAULT 1,
    `allowed_ips`     TEXT         NULL,
    `last_login_time` DATETIME     NULL,
    `last_login_ip`   VARCHAR(50)  NULL,
    `avatar`          VARCHAR(500) NULL,
    `company`         VARCHAR(200) NULL,
    `address`         VARCHAR(500) NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_username` (`username`),
    UNIQUE KEY `uk_user_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. 用户类型表
CREATE TABLE `user_type_list` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `created_at`   DATETIME     NOT NULL,
    `updated_at`   DATETIME     NOT NULL,
    `is_deleted`   TINYINT(1)   NOT NULL DEFAULT 0,
    `type_code`    VARCHAR(50)  NOT NULL,
    `type_name`    VARCHAR(50)  NOT NULL,
    `description`  VARCHAR(200) NULL,
    `default_role` VARCHAR(50)  NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_type_list_type_code` (`type_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. 企业表
CREATE TABLE `enterprise` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT,
    `created_at`      DATETIME      NOT NULL,
    `updated_at`      DATETIME      NOT NULL,
    `is_deleted`      TINYINT(1)    NOT NULL DEFAULT 0,
    `user_id`         BIGINT        NOT NULL,
    `enterprise_name` VARCHAR(200)  NOT NULL,
    `credit_code`     VARCHAR(18)   NOT NULL,
    `address`         VARCHAR(500)  NULL,
    `contact_person`  VARCHAR(50)   NULL,
    `contact_phone`   VARCHAR(20)   NULL,
    `industry`        VARCHAR(100)  NULL,
    `scale`           VARCHAR(50)   NULL,
    `carbon_quota`    DECIMAL(15,4) NOT NULL DEFAULT 0,
    `carbon_used`     DECIMAL(15,4) NOT NULL DEFAULT 0,
    `carbon_tradable` DECIMAL(15,4) NOT NULL DEFAULT 0,
    `license_url`     VARCHAR(500)  NULL,
    `cert_status`     INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_enterprise_user_id` (`user_id`),
    UNIQUE KEY `uk_enterprise_credit_code` (`credit_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. 审核员表
CREATE TABLE `reviewer` (
    `id`                    BIGINT       NOT NULL AUTO_INCREMENT,
    `created_at`            DATETIME     NOT NULL,
    `updated_at`            DATETIME     NOT NULL,
    `is_deleted`            TINYINT(1)   NOT NULL DEFAULT 0,
    `user_id`               BIGINT       NOT NULL,
    `qualification_no`      VARCHAR(50)  NOT NULL,
    `level`                 INT          NOT NULL DEFAULT 1,
    `organization`          VARCHAR(200) NULL,
    `reviewable_industries` TEXT         NULL,
    `completed_reviews`     INT          NOT NULL DEFAULT 0,
    `status`                INT          NOT NULL DEFAULT 1,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_reviewer_user_id` (`user_id`),
    UNIQUE KEY `uk_reviewer_qualification_no` (`qualification_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. 审核员资质表
CREATE TABLE `reviewer_qualification` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT,
    `created_at`        DATETIME     NOT NULL,
    `updated_at`        DATETIME     NOT NULL,
    `is_deleted`        TINYINT(1)   NOT NULL DEFAULT 0,
    `reviewer_id`       BIGINT       NOT NULL,
    `qualification_type` VARCHAR(100) NOT NULL,
    `certificate_no`    VARCHAR(50)  NOT NULL,
    `issuing_authority`  VARCHAR(200) NULL,
    `issued_date`       DATE         NULL,
    `expiry_date`       DATE         NULL,
    `status`            INT          NOT NULL DEFAULT 1,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. 第三方机构表
CREATE TABLE `third_party_org` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT,
    `created_at`        DATETIME     NOT NULL,
    `updated_at`        DATETIME     NOT NULL,
    `is_deleted`        TINYINT(1)   NOT NULL DEFAULT 0,
    `user_id`           BIGINT       NOT NULL,
    `org_name`          VARCHAR(200) NOT NULL,
    `org_code`          VARCHAR(50)  NOT NULL,
    `org_type`          INT          NOT NULL,
    `supervision_scope` TEXT         NULL,
    `contact_person`    VARCHAR(50)  NULL,
    `contact_phone`     VARCHAR(20)  NULL,
    `address`           VARCHAR(500) NULL,
    `access_level`      INT          NOT NULL DEFAULT 1,
    `status`            INT          NOT NULL DEFAULT 1,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_third_party_org_user_id` (`user_id`),
    UNIQUE KEY `uk_third_party_org_org_code` (`org_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. 认证机构表
CREATE TABLE `authenticator` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `created_at`     DATETIME     NOT NULL,
    `updated_at`     DATETIME     NOT NULL,
    `is_deleted`     TINYINT(1)   NOT NULL DEFAULT 0,
    `user_id`        BIGINT       NOT NULL,
    `org_name`       VARCHAR(200) NOT NULL,
    `org_code`       VARCHAR(50)  NOT NULL,
    `address`        VARCHAR(500) NULL,
    `contact_person` VARCHAR(50)  NULL,
    `contact_phone`  VARCHAR(20)  NULL,
    `cert_scope`     TEXT         NULL,
    `status`         INT          NOT NULL DEFAULT 1,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_authenticator_user_id` (`user_id`),
    UNIQUE KEY `uk_authenticator_org_code` (`org_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 8. 碳报告表
CREATE TABLE `carbon_report` (
    `id`                 BIGINT        NOT NULL AUTO_INCREMENT,
    `created_at`         DATETIME      NOT NULL,
    `updated_at`         DATETIME      NOT NULL,
    `is_deleted`         TINYINT(1)    NOT NULL DEFAULT 0,
    `report_no`          VARCHAR(50)   NOT NULL,
    `enterprise_id`      BIGINT        NOT NULL,
    `submitter_id`       BIGINT        NOT NULL,
    `accounting_period`  VARCHAR(20)   NOT NULL,
    `title`              VARCHAR(200)  NOT NULL,
    `report_type`        INT           NOT NULL,
    `emission_data`      TEXT          NOT NULL,
    `total_emission`     DECIMAL(15,4) NULL,
    `scope1_emission`    DECIMAL(15,4) NULL,
    `scope2_emission`    DECIMAL(15,4) NULL,
    `scope3_emission`    DECIMAL(15,4) NULL,
    `calculation_method` VARCHAR(100)  NULL,
    `status`             INT           NOT NULL DEFAULT 0,
    `reviewer_id`        BIGINT        NULL,
    `review_comment`     TEXT          NULL,
    `reviewed_at`        DATETIME      NULL,
    `signature_data`     TEXT          NULL,
    `blockchain_tx_hash` VARCHAR(255)  NULL,
    `on_chain_at`        DATETIME      NULL,
    `attachments`        TEXT          NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_carbon_report_report_no` (`report_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 9. 交易记录表
CREATE TABLE `transaction` (
    `id`                 BIGINT        NOT NULL AUTO_INCREMENT,
    `created_at`         DATETIME      NOT NULL,
    `updated_at`         DATETIME      NOT NULL,
    `is_deleted`         TINYINT(1)    NOT NULL DEFAULT 0,
    `trade_no`           VARCHAR(50)   NOT NULL,
    `trade_type`         INT           NOT NULL,
    `seller_id`          BIGINT        NOT NULL,
    `buyer_id`           BIGINT        NOT NULL,
    `quantity`           DECIMAL(15,4) NOT NULL,
    `unit_price`         DECIMAL(15,2) NOT NULL,
    `total_amount`       DECIMAL(15,2) NOT NULL,
    `report_id`          BIGINT        NULL,
    `status`             INT           NOT NULL DEFAULT 0,
    `remark`             TEXT          NULL,
    `blockchain_tx_hash` VARCHAR(255)  NULL,
    `completed_at`       DATETIME      NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_transaction_trade_no` (`trade_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 10. 账户权限定义表
CREATE TABLE `account_permission_list` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `created_at`      DATETIME     NOT NULL,
    `updated_at`      DATETIME     NOT NULL,
    `is_deleted`      TINYINT(1)   NOT NULL DEFAULT 0,
    `permission_name` VARCHAR(100) NOT NULL,
    `permission_code` VARCHAR(100) NOT NULL,
    `description`     VARCHAR(500) NULL,
    `module`          VARCHAR(50)  NULL,
    `sort_order`      INT          NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_account_permission_list_permission_code` (`permission_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 11. API权限表
CREATE TABLE `entry_permission` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `created_at`  DATETIME     NOT NULL,
    `updated_at`  DATETIME     NOT NULL,
    `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0,
    `user_type`   INT          NOT NULL,
    `api_path`    VARCHAR(255) NOT NULL,
    `http_method` VARCHAR(10)  NOT NULL,
    `is_allowed`  TINYINT(1)   NOT NULL DEFAULT 1,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 12. 拍卖订单表
CREATE TABLE `auction_order` (
    `id`               BIGINT        NOT NULL AUTO_INCREMENT,
    `created_at`       DATETIME      NOT NULL,
    `updated_at`       DATETIME      NOT NULL,
    `is_deleted`       TINYINT(1)    NOT NULL DEFAULT 0,
    `order_no`         VARCHAR(50)   NOT NULL,
    `user_id`          BIGINT        NOT NULL,
    `direction`        INT           NOT NULL,
    `quantity`         DECIMAL(15,4) NOT NULL,
    `price`            DECIMAL(15,2) NOT NULL,
    `matched_quantity` DECIMAL(15,4) NULL DEFAULT 0,
    `status`           INT           NOT NULL DEFAULT 0,
    `settlement_price` DECIMAL(15,2) NULL,
    `matched_at`       DATETIME      NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_auction_order_order_no` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 13. 撮合结果表
CREATE TABLE `matching_result` (
    `id`               BIGINT        NOT NULL AUTO_INCREMENT,
    `created_at`       DATETIME      NOT NULL,
    `updated_at`       DATETIME      NOT NULL,
    `is_deleted`       TINYINT(1)    NOT NULL DEFAULT 0,
    `match_no`         VARCHAR(50)   NOT NULL,
    `buy_order_id`     BIGINT        NOT NULL,
    `sell_order_id`    BIGINT        NOT NULL,
    `buyer_id`         BIGINT        NOT NULL,
    `seller_id`        BIGINT        NOT NULL,
    `matched_quantity` DECIMAL(15,4) NOT NULL,
    `settlement_price` DECIMAL(15,2) NOT NULL,
    `total_amount`     DECIMAL(15,2) NOT NULL,
    `status`           INT           NOT NULL DEFAULT 0,
    `transaction_id`   BIGINT        NULL,
    `settled_at`       DATETIME      NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_matching_result_match_no` (`match_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 14. RSA密钥对表
CREATE TABLE `rsa_key_pair` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT,
    `created_at`  DATETIME NOT NULL,
    `updated_at`  DATETIME NOT NULL,
    `is_deleted`  TINYINT(1) NOT NULL DEFAULT 0,
    `user_id`     BIGINT   NOT NULL,
    `public_key`  TEXT     NOT NULL,
    `private_key` TEXT     NOT NULL,
    `key_status`  INT      NOT NULL DEFAULT 1,
    `expires_at`  DATETIME NULL,
    `key_version` INT      NULL DEFAULT 1,
    `key_usage`   INT      NOT NULL DEFAULT 3,
    `remark`      VARCHAR(500) NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 15. 信誉评分表
CREATE TABLE `credit_score` (
    `id`               BIGINT     NOT NULL AUTO_INCREMENT,
    `created_at`       DATETIME   NOT NULL,
    `updated_at`       DATETIME   NOT NULL,
    `is_deleted`       TINYINT(1) NOT NULL DEFAULT 0,
    `enterprise_id`    BIGINT     NOT NULL,
    `score`            INT        NOT NULL DEFAULT 100,
    `level`            VARCHAR(20) NULL DEFAULT 'EXCELLENT',
    `trade_restricted` TINYINT(1) NOT NULL DEFAULT 0,
    `account_frozen`   TINYINT(1) NOT NULL DEFAULT 0,
    `last_evaluated_at` DATETIME  NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_credit_score_enterprise_id` (`enterprise_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 16. 信誉事件表
CREATE TABLE `credit_event` (
    `id`                BIGINT   NOT NULL AUTO_INCREMENT,
    `created_at`        DATETIME NOT NULL,
    `updated_at`        DATETIME NOT NULL,
    `is_deleted`        TINYINT(1) NOT NULL DEFAULT 0,
    `enterprise_id`     BIGINT   NOT NULL,
    `event_type`        INT      NOT NULL,
    `event_description` TEXT     NULL,
    `points_changed`    INT      NOT NULL,
    `score_before`      INT      NOT NULL,
    `score_after`       INT      NOT NULL,
    `related_report_id` BIGINT   NULL,
    `related_trade_id`  BIGINT   NULL,
    `triggered_by`      BIGINT   NULL,
    `triggered_at`      DATETIME NOT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 17. 碳币账户表
CREATE TABLE `carbon_coin_account` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT,
    `created_at`      DATETIME      NOT NULL,
    `updated_at`      DATETIME      NOT NULL,
    `is_deleted`      TINYINT(1)    NOT NULL DEFAULT 0,
    `user_id`         BIGINT        NOT NULL,
    `balance`         DECIMAL(15,2) NOT NULL DEFAULT 0,
    `total_recharged` DECIMAL(15,2) NULL DEFAULT 0,
    `total_spent`     DECIMAL(15,2) NULL DEFAULT 0,
    `status`          INT           NOT NULL DEFAULT 1,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_carbon_coin_account_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 18. 碳币交易记录表
CREATE TABLE `carbon_coin_transaction` (
    `id`               BIGINT        NOT NULL AUTO_INCREMENT,
    `created_at`       DATETIME      NOT NULL,
    `updated_at`       DATETIME      NOT NULL,
    `is_deleted`       TINYINT(1)    NOT NULL DEFAULT 0,
    `tx_no`            VARCHAR(50)   NOT NULL,
    `user_id`          BIGINT        NOT NULL,
    `tx_type`          INT           NOT NULL,
    `amount`           DECIMAL(15,2) NOT NULL,
    `balance_before`   DECIMAL(15,2) NOT NULL,
    `balance_after`    DECIMAL(15,2) NOT NULL,
    `related_quota`    DECIMAL(15,4) NULL,
    `related_trade_id` BIGINT        NULL,
    `counterpart_id`   BIGINT        NULL,
    `remark`           VARCHAR(500)  NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_carbon_coin_transaction_tx_no` (`tx_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 19. 排放评级表
CREATE TABLE `emission_rating` (
    `id`                 BIGINT        NOT NULL AUTO_INCREMENT,
    `created_at`         DATETIME      NOT NULL,
    `updated_at`         DATETIME      NOT NULL,
    `is_deleted`         TINYINT(1)    NOT NULL DEFAULT 0,
    `enterprise_id`      BIGINT        NOT NULL,
    `rating_year`        VARCHAR(4)    NOT NULL,
    `total_emission`     DECIMAL(15,4) NOT NULL,
    `emission_intensity` DECIMAL(15,4) NULL,
    `rating_level`       VARCHAR(1)    NOT NULL,
    `rating_score`       INT           NOT NULL,
    `percentile_rank`    INT           NULL,
    `reduction_ratio`    DECIMAL(5,2)  NULL,
    `rated_by`           BIGINT        NULL,
    `remark`             VARCHAR(1000) NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 20. 碳中和项目表
CREATE TABLE `carbon_neutral_project` (
    `id`                   BIGINT        NOT NULL AUTO_INCREMENT,
    `created_at`           DATETIME      NOT NULL,
    `updated_at`           DATETIME      NOT NULL,
    `is_deleted`           TINYINT(1)    NOT NULL DEFAULT 0,
    `project_no`           VARCHAR(50)   NOT NULL,
    `project_name`         VARCHAR(200)  NOT NULL,
    `project_type`         INT           NOT NULL,
    `owner_id`             BIGINT        NOT NULL,
    `description`          VARCHAR(2000) NULL,
    `location`             VARCHAR(200)  NULL,
    `expected_reduction`   DECIMAL(15,4) NULL,
    `actual_reduction`     DECIMAL(15,4) NULL,
    `investment_amount`    DECIMAL(15,2) NULL,
    `start_date`           DATE          NULL,
    `end_date`             DATE          NULL,
    `status`               INT           NOT NULL DEFAULT 0,
    `cert_status`          INT           NOT NULL DEFAULT 0,
    `cert_org`             VARCHAR(200)  NULL,
    `cert_date`            DATE          NULL,
    `cert_no`              VARCHAR(100)  NULL,
    `methodology`          VARCHAR(200)  NULL,
    `accounting_period`    INT           NULL,
    `issued_credits`       DECIMAL(15,4) NULL DEFAULT 0,
    `used_credits`         DECIMAL(15,4) NULL DEFAULT 0,
    `application_data`     TEXT          NULL,
    `verification_report`  TEXT          NULL,
    `attachments`          TEXT          NULL,
    `review_comment`       VARCHAR(1000) NULL,
    `reviewer_id`          BIGINT        NULL,
    `reviewed_at`          DATETIME      NULL,
    `monitoring_data`      TEXT          NULL,
    `last_monitoring_date` DATE          NULL,
    `verifier_id`          BIGINT        NULL,
    `verification_status`  INT           NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_carbon_neutral_project_project_no` (`project_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 21. 操作日志表
CREATE TABLE `operation_log` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `created_at`      DATETIME     NOT NULL,
    `updated_at`      DATETIME     NOT NULL,
    `is_deleted`      TINYINT(1)   NOT NULL DEFAULT 0,
    `user_id`         BIGINT       NOT NULL,
    `username`        VARCHAR(50)  NULL,
    `user_type`       INT          NOT NULL,
    `module`          VARCHAR(50)  NOT NULL,
    `action`          VARCHAR(50)  NOT NULL,
    `description`     VARCHAR(500) NULL,
    `http_method`     VARCHAR(10)  NULL,
    `request_url`     VARCHAR(255) NULL,
    `request_ip`      VARCHAR(50)  NULL,
    `request_params`  TEXT         NULL,
    `response_result` TEXT         NULL,
    `status`          INT          NOT NULL,
    `error_msg`       VARCHAR(1000) NULL,
    `execution_time`  BIGINT       NULL,
    `user_agent`      VARCHAR(500) NULL,
    PRIMARY KEY (`id`),
    INDEX `idx_operation_log_user_id` (`user_id`),
    INDEX `idx_operation_log_created_at` (`created_at`),
    INDEX `idx_operation_log_module` (`module`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
