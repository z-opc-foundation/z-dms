package com.zifang.z.dms.core.explorer;

import java.util.ArrayList;
import java.util.List;

/**
 * SQL 控制台执行回执。
 */
public class SqlExecutionResult {

    private String sourceId;
    /** 语句摘要（剥字面量、压空白、截断；不含原始参数值）。 */
    private String digest;
    /** 有结果集时为列名清单；无结果集（写语句）为空。 */
    private List<String> columns = new ArrayList<>();
    /** 结果行（与 columns 对齐；值一律 String，null 表示 NULL）。 */
    private List<List<String>> rows = new ArrayList<>();
    /** 有结果集 = true。 */
    private boolean hasResultSet;
    /** 写语句的影响行数（有结果集时为 -1）。 */
    private long updateCount = -1;
    /** 结果集是否被 maxRows 截断（截断只是展示限制，不是执行失败）。 */
    private boolean truncated;
    private long latencyMs;
    private long executedAt = System.currentTimeMillis();

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getDigest() {
        return digest;
    }

    public void setDigest(String digest) {
        this.digest = digest;
    }

    public List<String> getColumns() {
        return columns;
    }

    public void setColumns(List<String> columns) {
        this.columns = columns;
    }

    public List<List<String>> getRows() {
        return rows;
    }

    public void setRows(List<List<String>> rows) {
        this.rows = rows;
    }

    public boolean isHasResultSet() {
        return hasResultSet;
    }

    public void setHasResultSet(boolean hasResultSet) {
        this.hasResultSet = hasResultSet;
    }

    public long getUpdateCount() {
        return updateCount;
    }

    public void setUpdateCount(long updateCount) {
        this.updateCount = updateCount;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public void setTruncated(boolean truncated) {
        this.truncated = truncated;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public long getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(long executedAt) {
        this.executedAt = executedAt;
    }
}
