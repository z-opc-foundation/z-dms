package com.zifang.z.dms.core.service;

import com.zifang.z.dms.core.cache.DataSourceDefinitionCache;
import com.zifang.z.dms.core.event.DataSourceChangeEvent;
import com.zifang.z.dms.core.event.DataSourceChangePublisher;
import com.zifang.z.dms.core.event.DataSourceChangeType;
import com.zifang.z.dms.core.health.DataSourceHealthProber;
import com.zifang.z.dms.core.model.DataSourceDef;
import com.zifang.z.dms.core.model.DataSourceHealthReport;
import com.zifang.z.dms.core.model.DataSourceProbeResult;
import com.zifang.z.dms.core.pool.DataSourcePool;
import com.zifang.z.dms.core.pool.DataSourcePoolFactory;
import com.zifang.z.dms.core.registry.DataSourceRegistry;
import com.zifang.z.dms.core.store.DataSourceDefStore;
import com.zifang.z.dms.core.support.DataSourceDefValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 数据源登记服务（形态 A 嵌入式的主入口）。
 * <p>
 * 一条登记链：<b>校验 → 建池 → 探活 → 换绑(关旧池) → 缓存 → 落库 → 广播</b>。
 * <ul>
 *   <li><b>注册即探活</b>：建完池立刻真借一次连接跑 SELECT 1，结论随登记结果返回；</li>
 *   <li><b>换绑旧池显式关闭</b>：同 id 重新登记时旧池被 {@code close()}（不关就泄漏）；</li>
 *   <li><b>探活失败不静默</b>：失败会打 ERROR 日志、并进健康报告（宿主 /health 可见）。</li>
 * </ul>
 */
public class DataSourceService {

    private static final Logger log = LoggerFactory.getLogger(DataSourceService.class);

    private final DataSourceRegistry registry;
    private final DataSourcePoolFactory poolFactory;
    private final DataSourceHealthProber prober;
    private final DataSourceDefinitionCache cache;
    private final DataSourceDefStore store;
    private final DataSourceChangePublisher publisher;

    public DataSourceService(DataSourceRegistry registry,
                             DataSourcePoolFactory poolFactory,
                             DataSourceHealthProber prober,
                             DataSourceDefinitionCache cache,
                             DataSourceDefStore store,
                             DataSourceChangePublisher publisher) {
        this.registry = registry;
        this.poolFactory = poolFactory;
        this.prober = prober;
        this.cache = cache;
        this.store = store;
        this.publisher = publisher;
    }

    /**
     * 登记一个新数据源。
     *
     * @throws IllegalArgumentException 定义不合法（敏感键 / 缺 credentialRef 等）
     */
    public RegistrationResult register(DataSourceDef def) {
        DataSourceDefValidator.validateForRegistration(def);
        DataSourcePool pool = poolFactory.create(def);
        DataSourceProbeResult probe = prober.probe(pool);
        if (!probe.isUp()) {
            log.error("z-dms 登记即探活失败：数据源 [{}] credentialRef={} —— {}",
                    def.getId(), def.getCredentialRef(), probe.getDetail());
        } else {
            log.info("z-dms 登记成功：数据源 [{}] 探活 UP，延迟 {}ms", def.getId(), probe.getLatencyMs());
        }
        DataSourcePool previous = registry.register(pool);
        cache.put(def);
        if (store != null) {
            store.save(def);
        }
        DataSourceChangeType type = previous == null ? DataSourceChangeType.REGISTERED : DataSourceChangeType.UPDATED;
        fire(new DataSourceChangeEvent(type, def));
        return new RegistrationResult(def, probe, previous != null);
    }

    /**
     * 更新一个已登记的数据源（换绑：旧池显式关闭，新池重新探活）。
     */
    public RegistrationResult update(DataSourceDef def) {
        DataSourceDefValidator.validateForRegistration(def);
        if (!registry.contains(def.getId())) {
            throw new IllegalArgumentException("数据源未登记，无法更新: " + def.getId());
        }
        return register(def);
    }

    /** 摘除数据源（显式关闭池）。 */
    public void remove(String id) {
        DataSourcePool removed = registry.remove(id);
        DataSourceDef def = cache.remove(id);
        if (store != null) {
            store.delete(id);
        }
        if (removed != null || def != null) {
            fire(new DataSourceChangeEvent(DataSourceChangeType.REMOVED, def));
            log.info("z-dms 摘除数据源 [{}]", id);
        }
    }

    /** 已登记的定义（本地缓存）。 */
    public List<DataSourceDef> list() {
        return cache.list();
    }

    public DataSourceDef find(String id) {
        return cache.get(id);
    }

    /** 整体健康报告（宿主 /health 与 actuator 消费它）。 */
    public DataSourceHealthReport health() {
        return prober.report(registry.all());
    }

    /** 单池探活。 */
    public DataSourceProbeResult probe(String id) {
        return prober.probe(registry.require(id));
    }

    /** 取一个连接（宿主用它执行查询）。 */
    public DataSourcePool require(String id) {
        return registry.require(id);
    }

    /**
     * 应用远端推送过来的定义全量（z-config 长连接回调入口）。
     * <p>
     * 语义：远端列表里有的按登记/更新处理（换绑、关旧池），远端列表里没有的本地有 ⇒ 本地摘除。
     * 这就是「变更经 z-config 长连接推送」的落点：本地不再靠轮询元数据库。
     */
    public void applyRemoteDefinitions(List<DataSourceDef> remote) {
        Set<String> remoteIds = new LinkedHashSet<>();
        for (DataSourceDef def : remote == null ? new ArrayList<DataSourceDef>() : remote) {
            remoteIds.add(def.getId());
            DataSourceDefValidator.validateForRegistration(def);
            // 已存在的走换绑（关旧池），不存在的走新登记 —— register 内部按是否已有旧池分流。
            register(def);
        }
        List<String> toRemove = new ArrayList<>();
        for (DataSourceDef local : cache.list()) {
            if (!remoteIds.contains(local.getId())) {
                toRemove.add(local.getId());
            }
        }
        for (String id : toRemove) {
            remove(id);
        }
    }

    private void fire(DataSourceChangeEvent event) {
        cache.fire(event);
        if (publisher != null) {
            try {
                publisher.publish(event);
            } catch (RuntimeException e) {
                log.warn("z-dms 发布变更事件失败（不影响本地生效）: {}", e.getMessage());
            }
        }
    }
}
