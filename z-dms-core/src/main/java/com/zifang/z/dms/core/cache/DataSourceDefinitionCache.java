package com.zifang.z.dms.core.cache;

import com.zifang.z.dms.core.event.DataSourceChangeEvent;
import com.zifang.z.dms.core.event.DataSourceChangeListener;
import com.zifang.z.dms.core.model.DataSourceDef;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 本地定义缓存（形态 A 的关键取舍）：
 * <b>定义在进程内有一份缓存，变更靠 z-config 长连接推送而不是每次查库。</b>
 * <p>
 * 这样宿主的取数路径是「查内存 → 拿池」，不依赖元数据库的可用性；
 * 元数据库只在登记/更新/删除时写一次。
 */
public class DataSourceDefinitionCache {

    private final Map<String, DataSourceDef> definitions = new ConcurrentHashMap<>();
    private final List<DataSourceChangeListener> listeners = new CopyOnWriteArrayList<>();

    public void addListener(DataSourceChangeListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public List<DataSourceDef> list() {
        return new ArrayList<>(definitions.values());
    }

    public DataSourceDef get(String id) {
        return definitions.get(id);
    }

    public boolean contains(String id) {
        return definitions.containsKey(id);
    }

    public DataSourceDef put(DataSourceDef def) {
        return definitions.put(def.getId(), def.copy());
    }

    public DataSourceDef remove(String id) {
        return definitions.remove(id);
    }

    public int size() {
        return definitions.size();
    }

    public void clear() {
        definitions.clear();
    }

    /** 广播变更（进程内监听器）。 */
    public void fire(DataSourceChangeEvent event) {
        for (DataSourceChangeListener listener : listeners) {
            try {
                listener.onChange(event);
            } catch (RuntimeException e) {
                // 监听器炸了不能拖垮登记主流程
                System.err.println("[z-dms] 变更监听器异常: " + e.getMessage());
            }
        }
    }
}
