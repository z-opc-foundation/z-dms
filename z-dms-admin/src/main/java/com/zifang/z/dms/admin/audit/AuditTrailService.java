package com.zifang.z.dms.admin.audit;

import com.zifang.z.dms.admin.store.JdbcChangeLogStore;
import com.zifang.z.dms.admin.store.JdbcUsageLogStore;
import com.zifang.z.dms.core.model.DataSourceDef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 审计面服务：<b>变更与使用都留痕</b>。
 *
 * <p>两条流水：
 * <ul>
 *   <li><b>变更</b>（{@code z_dms_datasource_change_log}）：谁、何时、把哪个源从什么改成了什么 ——
 *       带<b>前后值快照</b>（{@code beforeValue} / {@code afterValue}），变更历史界面据此回看；</li>
 *   <li><b>使用</b>（{@code z_dms_datasource_usage_log}）：谁、何时、用了哪个源、结论如何、耗时多少。</li>
 * </ul>
 *
 * <p><b>审计写失败不阻断业务动作</b>：登记成功但审计写不进去时记 ERROR 日志，
 * 不能因为审计表抖动就把用户的登记操作搞失败（但错误一定要看得见）。
 */
public class AuditTrailService {

    private static final Logger log = LoggerFactory.getLogger(AuditTrailService.class);

    private final JdbcChangeLogStore changeLogStore;
    private final JdbcUsageLogStore usageLogStore;

    public AuditTrailService(JdbcChangeLogStore changeLogStore, JdbcUsageLogStore usageLogStore) {
        this.changeLogStore = changeLogStore;
        this.usageLogStore = usageLogStore;
    }

    /**
     * 记一条变更流水（含前后值快照）。
     *
     * @param changeType REGISTERED / UPDATED / REMOVED
     * @param before     变更前的定义（新增时传 null）
     * @param after      变更后的定义（删除时传 null）
     */
    public void recordChange(String datasourceId, String changeType, DataSourceDef before,
                              DataSourceDef after, String operator, String detail) {
        try {
            ChangeRecord record = new ChangeRecord();
            record.setDatasourceId(datasourceId);
            record.setChangeType(changeType);
            record.setOperator(operator);
            record.setDetail(detail);
            record.setBeforeValue(ChangeSnapshot.of(before));
            record.setAfterValue(ChangeSnapshot.of(after));
            DataSourceDef ref = after != null ? after : before;
            record.setCredentialRef(ref == null ? null : ref.getCredentialRef());
            record.setChangedAt(System.currentTimeMillis());
            changeLogStore.save(record);
        } catch (RuntimeException e) {
            log.error("z-dms 写变更流水失败（数据源 [{}]，类型 {}）: {}", datasourceId, changeType, e.getMessage());
        }
    }

    /** 记一条使用流水。 */
    public void recordUsage(String datasourceId, String action, String operator, boolean success,
                            String detail, Long latencyMs) {
        try {
            UsageRecord record = new UsageRecord();
            record.setDatasourceId(datasourceId);
            record.setAction(action);
            record.setOperator(operator);
            record.setSuccess(success);
            record.setDetail(detail);
            record.setLatencyMs(latencyMs);
            record.setUsedAt(System.currentTimeMillis());
            usageLogStore.save(record);
        } catch (RuntimeException e) {
            log.error("z-dms 写使用流水失败（数据源 [{}]，动作 {}）: {}", datasourceId, action, e.getMessage());
        }
    }

    /** 变更历史。 */
    public List<ChangeRecord> listChanges(String datasourceId, int limit) {
        try {
            return changeLogStore.list(datasourceId, limit);
        } catch (RuntimeException e) {
            log.error("z-dms 读变更流水失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /** 使用流水。 */
    public List<UsageRecord> listUsages(String datasourceId, int limit) {
        try {
            return usageLogStore.list(datasourceId, limit);
        } catch (RuntimeException e) {
            log.error("z-dms 读使用流水失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 审计流水（变更 + 使用合成一条流，按时间倒序）。
     *
     * <p>合成一条流是为了界面「审计流水」页能一屏读到底：
     * 变更与使用混在时间轴上，才看得出「谁先改了什么、随后谁去用了它」。
     */
    public List<AuditEntry> listAudit(String datasourceId, int limit) {
        List<AuditEntry> out = new ArrayList<>();
        for (ChangeRecord c : listChanges(datasourceId, limit)) {
            AuditEntry e = new AuditEntry();
            e.setCategory("CHANGE");
            e.setDatasourceId(c.getDatasourceId());
            e.setAction(c.getChangeType());
            e.setOperator(c.getOperator());
            e.setSuccess(true);
            e.setDetail(c.getDetail());
            e.setOccurredAt(c.getChangedAt());
            e.setBeforeValue(c.getBeforeValue());
            e.setAfterValue(c.getAfterValue());
            out.add(e);
        }
        for (UsageRecord u : listUsages(datasourceId, limit)) {
            AuditEntry e = new AuditEntry();
            e.setCategory("USAGE");
            e.setDatasourceId(u.getDatasourceId());
            e.setAction(u.getAction());
            e.setOperator(u.getOperator());
            e.setSuccess(u.isSuccess());
            e.setDetail(u.getDetail());
            e.setLatencyMs(u.getLatencyMs());
            e.setOccurredAt(u.getUsedAt());
            out.add(e);
        }
        Collections.sort(out, new Comparator<AuditEntry>() {
            @Override
            public int compare(AuditEntry a, AuditEntry b) {
                return Long.compare(b.getOccurredAt(), a.getOccurredAt());
            }
        });
        return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }
}
