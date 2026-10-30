-- =====================================================================================
-- z-dms 数据源使用流水表（形态 B web 管控 · 审计面的另一半）
--
-- 变更流水（z_dms_datasource_change_log）记的是「谁改了什么」；
-- 这张表记的是「谁在什么时候用了哪个数据源」——
--   审计面要求变更与使用**都**留痕，只有变更没有使用，事后追不到「这条数据是谁拉走的」。
--
-- 幂等口径：CREATE TABLE IF NOT EXISTS，可重复执行（两遍 exit=0）。
--
-- 纪律：**不记任何明文凭据**。detail 里只放结论与耗时，
--   失败时的根因串（Connection refused / Access denied 等）不含口令。
-- =====================================================================================

CREATE TABLE IF NOT EXISTS `z_dms_datasource_usage_log` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `datasource_id` VARCHAR(64)  NOT NULL COMMENT '被使用的数据源标识',
    `action`        VARCHAR(32)  NOT NULL COMMENT 'TEST_CONNECTION / ACQUIRE_CONNECTION 等动作',
    `operator`      VARCHAR(64)  DEFAULT NULL COMMENT '操作人（登录用户 / 调用方标识）',
    `success`       TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '1=成功 0=失败',
    `detail`        VARCHAR(512) DEFAULT NULL COMMENT '结论摘要（如 SELECT 1 ok / 根因），不得含明文凭据',
    `latency_ms`    BIGINT       DEFAULT NULL COMMENT '耗时（毫秒）；失败时为 NULL',
    `used_at`       DATETIME     NOT NULL COMMENT '使用时间',
    PRIMARY KEY (`id`),
    KEY `idx_datasource_id` (`datasource_id`),
    KEY `idx_used_at` (`used_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = 'z-dms 数据源使用流水表';
