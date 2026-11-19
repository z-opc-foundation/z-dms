-- =====================================================================================
-- V5: SQL 控制台执行历史（z-dms 形态 B）
--
-- 幂等：CREATE TABLE IF NOT EXISTS，执行两遍无报错。
-- 表里只有语句文本与结论：凭据永远不在这张表里（连接走已登记数据源的池，凭据由
-- z-mist 按 credentialRef 在服务端内存解析，不进任何 SQL 文本）。
-- =====================================================================================

CREATE TABLE IF NOT EXISTS z_dms_sql_history (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    datasource_id  VARCHAR(128)  NOT NULL COMMENT '执行目标数据源 id',
    operator       VARCHAR(128)  NOT NULL COMMENT '操作人（登录身份或 X-Operator）',
    sql_digest     VARCHAR(512)  NULL     COMMENT '语句摘要（字面量抹除后的形态，审计可读）',
    sql_text       TEXT          NOT NULL COMMENT '用户提交的原始语句',
    statement_kind VARCHAR(16)   NOT NULL COMMENT 'READ / WRITE',
    success        TINYINT       NOT NULL DEFAULT 0 COMMENT '1=成功 0=失败',
    row_count      BIGINT        NOT NULL DEFAULT 0 COMMENT '结果行数（查询）或影响行数（写）',
    latency_ms     BIGINT        NULL     COMMENT '执行耗时（毫秒）',
    error_message  VARCHAR(1024) NULL     COMMENT '失败根因（成功为空）',
    executed_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_zdms_sql_history_ds_time (datasource_id, executed_at),
    KEY idx_zdms_sql_history_time (executed_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'z-dms SQL 控制台执行历史';
