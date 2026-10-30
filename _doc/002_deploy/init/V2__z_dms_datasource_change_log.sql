-- =====================================================================================
-- z-dms 数据源变更流水表（形态 B web 管控的审计面；形态 A 只写不改，能力先建表）
--
-- 幂等口径：本脚本可重复执行（CREATE TABLE IF NOT EXISTS），执行两遍无报错。
-- 纪律：变更流水里同样**不得出现明文凭据** —— 只记 credential_ref 的引用与操作人/来源。
-- =====================================================================================

CREATE TABLE IF NOT EXISTS `z_dms_datasource_change_log` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `datasource_id`  VARCHAR(64)  NOT NULL COMMENT '数据源标识',
    `change_type`    VARCHAR(16)  NOT NULL COMMENT 'REGISTERED / UPDATED / REMOVED',
    `credential_ref` VARCHAR(255) DEFAULT NULL COMMENT '当时的凭据引用（z-mist 键名）',
    `operator`       VARCHAR(64)  DEFAULT NULL COMMENT '操作人 / 来源（API 调用方或 z-config 推送）',
    `detail`         VARCHAR(512) DEFAULT NULL COMMENT '摘要（不得含明文凭据）',
    `changed_at`     DATETIME     NOT NULL COMMENT '变更时间',
    PRIMARY KEY (`id`),
    KEY `idx_datasource_id` (`datasource_id`),
    KEY `idx_changed_at` (`changed_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = 'z-dms 数据源变更流水表';
