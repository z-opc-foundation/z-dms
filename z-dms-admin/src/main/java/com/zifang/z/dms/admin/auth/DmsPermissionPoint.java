package com.zifang.z.dms.admin.auth;

/**
 * z-dms 权限点（资源码）。
 *
 * <p>命名风格照 z-team（唯一跑通的 z-ctc 消费方）：全大写下划线，
 * 统一挂 {@code app_code = z-dms}。这些码要在 4A 中心的
 * {@code z_ctc_spc_resource} 表里注册后授权才生效
 * （登记脚本见 {@code _doc/002_deploy/init/z-dms-permission-points.sql}，
 * 或走 4A 中心管理界面）。
 */
public enum DmsPermissionPoint {

    DMS_GLOBAL_ADMIN(0, "全局管理员", "拥有 z-dms 全部权限（含写 SQL 执行）"),
    DMS_SOURCE_VIEW(10, "数据源查看", "查看数据源清单、健康态、测连接"),
    DMS_SOURCE_MANAGE(20, "数据源管理", "登记 / 修改 / 摘除数据源"),
    DMS_SCHEMA_VIEW(30, "库表结构查看", "浏览库、表、字段、索引与建表语句"),
    DMS_DATA_VIEW(40, "表数据查看", "分页浏览表数据"),
    DMS_SQL_EXECUTE(50, "SQL 执行（查询）", "在 SQL 控制台执行查询类语句"),
    DMS_SQL_WRITE(60, "SQL 执行（写）", "执行写语句（还需服务端开启写开关）"),
    DMS_AUDIT_VIEW(70, "审计查看", "查看变更历史、审计流水与查询历史");

    private final int sortOrder;
    private final String label;
    private final String description;

    DmsPermissionPoint(int sortOrder, String label, String description) {
        this.sortOrder = sortOrder;
        this.label = label;
        this.description = description;
    }

    /** 资源码（进 z_ctc_spc_resource.resource_code，也是权限判定用的那把钥匙）。 */
    public String getCode() {
        return name();
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
