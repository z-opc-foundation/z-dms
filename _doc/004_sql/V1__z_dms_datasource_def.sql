-- =====================================================================================
-- z-dms 数据源定义表（形态 A / 形态 B 共用）
--
-- 幂等口径：本脚本可重复执行（CREATE TABLE IF NOT EXISTS），执行两遍无报错。
-- 库名口径：组织中央库 `oc`（生产建表由 DBA/运维走正规流程；本仓自检只在本机隔离实例跑）。
--
-- 凭据纪律（组织 008 凭证规范 / TASK-20261012-009 §4）：
--   * 本表**没有**任何明文口令列，也不会有「以后再说」的口子；
--   * 凭据一律以 credential_ref 存 z-mist 键名（格式 <group>/<key>）；
--   * properties 存的是非敏感连接属性（host / port / database / 池参数等），
--     写入口由 DataSourceDefValidator 拦敏感键（password / secret / token / ... 共 30 余种形态）。
-- =====================================================================================

CREATE TABLE IF NOT EXISTS `z_dms_datasource_def` (
    `id`            VARCHAR(64)  NOT NULL COMMENT '数据源稳定标识（删除后不复用）',
    `name`          VARCHAR(128) NOT NULL COMMENT '展示名',
    `type`          VARCHAR(16)  NOT NULL COMMENT '类型：MEMORY / JDBC / CSV / HTTP / SQL',
    `credential_ref` VARCHAR(255) DEFAULT NULL COMMENT 'z-mist 键名引用，格式 <group>/<key>；永不含明文口令',
    `properties`    TEXT         DEFAULT NULL COMMENT '非敏感连接属性（JSON）',
    `created_at`    DATETIME     NOT NULL COMMENT '创建时间',
    `updated_at`    DATETIME     NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = 'z-dms 数据源定义表';
