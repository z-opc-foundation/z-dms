package com.zifang.z.dms.core.event;

import com.zifang.z.dms.core.model.DataSourceDef;

/**
 * 数据源定义变更事件。
 * <p>
 * <b>事件里只有 credentialRef（引用），没有明文。</b>
 */
public final class DataSourceChangeEvent {

    private final DataSourceChangeType type;
    private final DataSourceDef definition;

    public DataSourceChangeEvent(DataSourceChangeType type, DataSourceDef definition) {
        this.type = type;
        this.definition = definition == null ? null : definition.copy();
    }

    public DataSourceChangeType getType() {
        return type;
    }

    public DataSourceDef getDefinition() {
        return definition == null ? null : definition.copy();
    }

    public String getId() {
        return definition == null ? null : definition.getId();
    }

    @Override
    public String toString() {
        return "DataSourceChangeEvent{type=" + type + ", def=" + definition + "}";
    }
}
