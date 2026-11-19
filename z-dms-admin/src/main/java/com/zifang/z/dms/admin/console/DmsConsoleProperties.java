package com.zifang.z.dms.admin.console;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 数据浏览与 SQL 控制台的配置面（前缀 {@code z.dms.console}）。
 * <p>
 * 默认值刻意偏保守：写语句默认关、单次行数 / 超时 / 语句长度都有上限 ——
 * 这是个「能看能查」的管控台，不是裸的数据库客户端。
 */
@ConfigurationProperties(prefix = "z.dms.console")
public class DmsConsoleProperties {

    /** 是否允许写语句（UPDATE / DELETE / DDL…）。默认关：放开需要 z.dms.console.write-enabled=true + 用户有 DMS_SQL_WRITE。 */
    private boolean writeEnabled = false;

    /** SQL 执行单次返回行数上限（用户要更多时截断并提示）。 */
    private int maxRows = 1000;

    /** SQL 执行超时（秒）。 */
    private int timeoutSeconds = 30;

    /** 单条 SQL 最大长度（字符）。 */
    private int maxSqlLength = 10000;

    /** 数据浏览单页行数上限。 */
    private int maxPageSize = 500;

    /** SQL 执行历史在元数据库里保留的条数上限（超出滚动淘汰最旧）。 */
    private int historyLimit = 200;

    public boolean isWriteEnabled() {
        return writeEnabled;
    }

    public void setWriteEnabled(boolean writeEnabled) {
        this.writeEnabled = writeEnabled;
    }

    public int getMaxRows() {
        return maxRows;
    }

    public void setMaxRows(int maxRows) {
        this.maxRows = maxRows;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getMaxSqlLength() {
        return maxSqlLength;
    }

    public void setMaxSqlLength(int maxSqlLength) {
        this.maxSqlLength = maxSqlLength;
    }

    public int getMaxPageSize() {
        return maxPageSize;
    }

    public void setMaxPageSize(int maxPageSize) {
        this.maxPageSize = maxPageSize;
    }

    public int getHistoryLimit() {
        return historyLimit;
    }

    public void setHistoryLimit(int historyLimit) {
        this.historyLimit = historyLimit;
    }
}
