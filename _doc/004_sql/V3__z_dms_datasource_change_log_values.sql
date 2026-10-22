-- =====================================================================================
-- z-dms 变更流水表（形态 B web 管控）· V3：补「前后值」两列
--
-- 为什么是 V3 而不是改 V2：
--   V2 已经发布并在 TASK-20261012-011 里被验证过（两遍执行 exit=0），
--   改历史脚本会让那批证据对不上；所以这里走**增量**：V3 先按最终形态建表
--   （表不存在时 CREATE 出来就带两列），再对已存在的旧表做**幂等 ALTER** 补列。
--   ⇒ V2 先跑或后跑都能得到同一张表；V3 自己跑两遍也 exit=0。
--
-- 幂等口径：CREATE TABLE IF NOT EXISTS + information_schema 判列存在再 ALTER
--   （MySQL 没有 ADD COLUMN IF NOT EXISTS，用 PREPARE 动态 SQL 实现等价语义）。
--
-- 纪律：前后值快照里**同样不得出现明文凭据** —— 快照只含
--   id / name / type / credentialRef / 非敏感 properties（写入口由 DataSourceDefValidator 拦）。
-- =====================================================================================

CREATE TABLE IF NOT EXISTS `z_dms_datasource_change_log` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `datasource_id`  VARCHAR(64)  NOT NULL COMMENT '数据源标识',
    `change_type`    VARCHAR(16)  NOT NULL COMMENT 'REGISTERED / UPDATED / REMOVED',
    `credential_ref` VARCHAR(255) DEFAULT NULL COMMENT '当时的凭据引用（z-mist 键名）',
    `operator`       VARCHAR(64)  DEFAULT NULL COMMENT '操作人 / 来源（API 调用方或 z-config 推送）',
    `detail`         VARCHAR(512) DEFAULT NULL COMMENT '摘要（不得含明文凭据）',
    `before_value`   TEXT         DEFAULT NULL COMMENT '变更前的定义快照（JSON；新增时为 NULL）',
    `after_value`    TEXT         DEFAULT NULL COMMENT '变更后的定义快照（JSON；删除时为 NULL）',
    `changed_at`     DATETIME     NOT NULL COMMENT '变更时间',
    PRIMARY KEY (`id`),
    KEY `idx_datasource_id` (`datasource_id`),
    KEY `idx_changed_at` (`changed_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = 'z-dms 数据源变更流水表（含前后值快照）';

-- ---- 旧表（只跑过 V2 的环境）补列：before_value -------------------------------------------
SET @c1 := (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'z_dms_datasource_change_log'
              AND COLUMN_NAME = 'before_value');
SET @s1 := IF(@c1 = 0,
    'ALTER TABLE `z_dms_datasource_change_log` ADD COLUMN `before_value` TEXT DEFAULT NULL COMMENT ''变更前的定义快照（JSON；新增时为 NULL）''',
    'DO 0');
PREPARE st1 FROM @s1;
EXECUTE st1;
DEALLOCATE PREPARE st1;

-- ---- 旧表（只跑过 V2 的环境）补列：after_value --------------------------------------------
SET @c2 := (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'z_dms_datasource_change_log'
              AND COLUMN_NAME = 'after_value');
SET @s2 := IF(@c2 = 0,
    'ALTER TABLE `z_dms_datasource_change_log` ADD COLUMN `after_value` TEXT DEFAULT NULL COMMENT ''变更后的定义快照（JSON；删除时为 NULL）''',
    'DO 0');
PREPARE st2 FROM @s2;
EXECUTE st2;
DEALLOCATE PREPARE st2;
