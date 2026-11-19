-- =====================================================================================
-- z-dms 权限点登记（在 z-ctc 4A 中心的库里执行；幂等，重复执行无副作用）
--
-- 权限点 = z_ctc_spc_resource 里的资源行（app_code='z-dms'，resource_type=1 功能权限）。
-- 授权：执行本脚本后在 4A 中心管理界面把权限点授给用户/角色；
--       ZCtcClient 没有 createResource 接口，登记只能落 SQL（本脚本）或走界面。
-- 幂等手法：INSERT ... SELECT ... WHERE NOT EXISTS（tenant_code 为 NULL 时唯一键不生效，
--           不能用 ON DUPLICATE KEY 去重）。
-- =====================================================================================

INSERT INTO z_ctc_spc_resource
    (resource_code, resource_name, resource_type, app_code, sort_order, tenant_code, description, status)
SELECT 'DMS_GLOBAL_ADMIN', '全局管理员', 1, 'z-dms', 0, NULL,
       '拥有 z-dms 全部权限（含写 SQL 执行）', 1
WHERE NOT EXISTS (SELECT 1 FROM z_ctc_spc_resource
                  WHERE resource_code = 'DMS_GLOBAL_ADMIN' AND app_code = 'z-dms');

INSERT INTO z_ctc_spc_resource
    (resource_code, resource_name, resource_type, app_code, sort_order, tenant_code, description, status)
SELECT 'DMS_SOURCE_VIEW', '数据源查看', 1, 'z-dms', 10, NULL,
       '查看数据源清单、健康态、测连接', 1
WHERE NOT EXISTS (SELECT 1 FROM z_ctc_spc_resource
                  WHERE resource_code = 'DMS_SOURCE_VIEW' AND app_code = 'z-dms');

INSERT INTO z_ctc_spc_resource
    (resource_code, resource_name, resource_type, app_code, sort_order, tenant_code, description, status)
SELECT 'DMS_SOURCE_MANAGE', '数据源管理', 1, 'z-dms', 20, NULL,
       '登记 / 修改 / 摘除数据源', 1
WHERE NOT EXISTS (SELECT 1 FROM z_ctc_spc_resource
                  WHERE resource_code = 'DMS_SOURCE_MANAGE' AND app_code = 'z-dms');

INSERT INTO z_ctc_spc_resource
    (resource_code, resource_name, resource_type, app_code, sort_order, tenant_code, description, status)
SELECT 'DMS_SCHEMA_VIEW', '库表结构查看', 1, 'z-dms', 30, NULL,
       '浏览库、表、字段、索引与建表语句', 1
WHERE NOT EXISTS (SELECT 1 FROM z_ctc_spc_resource
                  WHERE resource_code = 'DMS_SCHEMA_VIEW' AND app_code = 'z-dms');

INSERT INTO z_ctc_spc_resource
    (resource_code, resource_name, resource_type, app_code, sort_order, tenant_code, description, status)
SELECT 'DMS_DATA_VIEW', '表数据查看', 1, 'z-dms', 40, NULL,
       '分页浏览表数据', 1
WHERE NOT EXISTS (SELECT 1 FROM z_ctc_spc_resource
                  WHERE resource_code = 'DMS_DATA_VIEW' AND app_code = 'z-dms');

INSERT INTO z_ctc_spc_resource
    (resource_code, resource_name, resource_type, app_code, sort_order, tenant_code, description, status)
SELECT 'DMS_SQL_EXECUTE', 'SQL 执行（查询）', 1, 'z-dms', 50, NULL,
       '在 SQL 控制台执行查询类语句', 1
WHERE NOT EXISTS (SELECT 1 FROM z_ctc_spc_resource
                  WHERE resource_code = 'DMS_SQL_EXECUTE' AND app_code = 'z-dms');

INSERT INTO z_ctc_spc_resource
    (resource_code, resource_name, resource_type, app_code, sort_order, tenant_code, description, status)
SELECT 'DMS_SQL_WRITE', 'SQL 执行（写）', 1, 'z-dms', 60, NULL,
       '执行写语句（还需服务端 z.dms.console.write-enabled=true）', 1
WHERE NOT EXISTS (SELECT 1 FROM z_ctc_spc_resource
                  WHERE resource_code = 'DMS_SQL_WRITE' AND app_code = 'z-dms');

INSERT INTO z_ctc_spc_resource
    (resource_code, resource_name, resource_type, app_code, sort_order, tenant_code, description, status)
SELECT 'DMS_AUDIT_VIEW', '审计查看', 1, 'z-dms', 70, NULL,
       '查看变更历史、审计流水与 SQL 执行历史', 1
WHERE NOT EXISTS (SELECT 1 FROM z_ctc_spc_resource
                  WHERE resource_code = 'DMS_AUDIT_VIEW' AND app_code = 'z-dms');
