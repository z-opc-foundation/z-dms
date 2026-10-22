package com.zifang.z.dms.starter.event;

import com.zifang.util.core.meta.Result;
import com.zifang.z.config.client.config.ZConfigService;
import com.zifang.z.config.client.config.listener.ZConfigListener;
import com.zifang.z.dms.core.model.DataSourceDef;
import com.zifang.z.dms.core.service.DataSourceService;
import com.zifang.z.dms.core.support.DataSourceDefJson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

/**
 * z-config 长连接桥：<b>定义变更经 z-config 长连接推送</b>。
 * <p>
 * 两个方向：
 * <ul>
 *   <li><b>收</b>：{@code ZConfigService#addListener} 订阅 dataId ⇒ 长连接推来的内容
 *       解析成定义全量 ⇒ {@link DataSourceService#applyRemoteDefinitions} 应用
 *       （有的换绑、关旧池；远端没有的本地摘除）。</li>
 *   <li><b>发</b>：本进程的定义变更 ⇒ 把全量定义 JSON 回推 z-config（{@code saveConfig}），
 *       让别的进程经长连接收到。</li>
 * </ul>
 * <b>推送内容里只有 credentialRef（引用），没有明文口令。</b>
 * <p>
 * 服务是<b>延迟取</b>的（{@link ObjectProvider}）：服务本身依赖发布器、发布器依赖本 bridge，
 * 构造期直接注入会成环。
 */
public class ZConfigDataSourceChangeBridge implements ZConfigListener {

    private static final Logger log = LoggerFactory.getLogger(ZConfigDataSourceChangeBridge.class);

    private final ZConfigService zConfigService;
    private final ObjectProvider<DataSourceService> serviceProvider;
    private final String group;
    private final String dataId;
    private final boolean publish;

    public ZConfigDataSourceChangeBridge(ZConfigService zConfigService,
                                         ObjectProvider<DataSourceService> serviceProvider,
                                         String group,
                                         String dataId,
                                         boolean publish) {
        this.zConfigService = zConfigService;
        this.serviceProvider = serviceProvider;
        this.group = group;
        this.dataId = dataId;
        this.publish = publish;
    }

    /** 订阅 + 拉一次当前值。z-config 不可达时<b>不阻断宿主启动</b>，只打日志。 */
    public void start() {
        try {
            zConfigService.addListener(dataId, group, this);
            log.info("z-dms 已订阅 z-config 长连接：group={} dataId={}", group, dataId);
        } catch (Exception e) {
            log.warn("z-dms 订阅 z-config 失败（不阻断宿主启动）: {}", e.getMessage());
        }
        try {
            Result<String> current = zConfigService.getConfig(group, dataId, 3000L);
            if (current != null && current.isSuccess() && current.getData() != null
                    && !current.getData().trim().isEmpty()) {
                apply(current.getData());
            }
        } catch (Exception e) {
            log.warn("z-dms 拉取 z-config 当前配置失败（不阻断宿主启动）: {}", e.getMessage());
        }
    }

    /** 长连接回调：内容变了就应用。 */
    @Override
    public void receiveConfigInfo(String newConfig) {
        apply(newConfig);
    }

    private void apply(String content) {
        DataSourceService service = serviceProvider.getIfAvailable();
        if (service == null) {
            log.warn("z-dms 收到 z-config 推送但本地服务尚未就绪，本次忽略");
            return;
        }
        try {
            List<DataSourceDef> defs = DataSourceDefJson.fromJson(content);
            service.applyRemoteDefinitions(defs);
            log.info("z-dms 应用 z-config 推送的数据源定义 {} 条", defs.size());
        } catch (Exception e) {
            log.error("z-dms 应用 z-config 推送失败: {}", e.getMessage(), e);
        }
    }

    /** 把当前定义全量推回 z-config（发布方向）。 */
    public void publishCurrent() {
        if (!publish) {
            return;
        }
        DataSourceService service = serviceProvider.getIfAvailable();
        if (service == null) {
            return;
        }
        try {
            List<DataSourceDef> defs = service.list();
            zConfigService.saveConfig(group, dataId, DataSourceDefJson.toJson(defs));
            log.info("z-dms 已把 {} 条数据源定义推送到 z-config（group={} dataId={}）",
                    defs.size(), group, dataId);
        } catch (Exception e) {
            log.warn("z-dms 推送定义到 z-config 失败（本地已生效）: {}", e.getMessage());
        }
    }
}
