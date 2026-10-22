package com.zifang.z.dms.admin.audit;

/**
 * 审计流水的一条统一视图（变更 + 使用合成一条流，便于界面按时间倒序读）。
 *
 * <p>{@code category} 取值：{@code CHANGE}（谁改了什么）/ {@code USAGE}（谁用了哪个源）。
 */
public class AuditEntry {

    private String category;
    private String datasourceId;
    /** 变更时是 REGISTERED/UPDATED/REMOVED；使用时是动作名（如 TEST_CONNECTION）。 */
    private String action;
    private String operator;
    private boolean success;
    private String detail;
    private Long latencyMs;
    private long occurredAt;
    private String beforeValue;
    private String afterValue;

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDatasourceId() {
        return datasourceId;
    }

    public void setDatasourceId(String datasourceId) {
        this.datasourceId = datasourceId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public long getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(long occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getBeforeValue() {
        return beforeValue;
    }

    public void setBeforeValue(String beforeValue) {
        this.beforeValue = beforeValue;
    }

    public String getAfterValue() {
        return afterValue;
    }

    public void setAfterValue(String afterValue) {
        this.afterValue = afterValue;
    }
}
