package com.zifang.z.dms.core.service;

import com.zifang.z.dms.core.model.DataSourceDef;
import com.zifang.z.dms.core.model.DataSourceProbeResult;

/**
 * 登记结果：定义 + 本次登记即触发的探活结论 + 是否换掉了旧池。
 * <p>
 * <b>探活结论随登记结果一起返回</b>：调用方不必再问一次「到底连上没有」。
 */
public final class RegistrationResult {

    private final DataSourceDef definition;
    private final DataSourceProbeResult probe;
    private final boolean replaced;

    public RegistrationResult(DataSourceDef definition, DataSourceProbeResult probe, boolean replaced) {
        this.definition = definition;
        this.probe = probe;
        this.replaced = replaced;
    }

    public DataSourceDef getDefinition() {
        return definition;
    }

    public DataSourceProbeResult getProbe() {
        return probe;
    }

    /** true = 同 id 之前有池，已被显式关闭并换绑。 */
    public boolean isReplaced() {
        return replaced;
    }

    public boolean isUp() {
        return probe != null && probe.isUp();
    }

    @Override
    public String toString() {
        return "RegistrationResult{def=" + definition + ", probe=" + probe + ", replaced=" + replaced + "}";
    }
}
