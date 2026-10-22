package com.zifang.z.dms.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * z-dms 形态 B：组织级 web 管控宿主。
 *
 * <p>与形态 A（{@code z-dms-spring-boot-starter} 嵌入式）的关系：
 * <ul>
 *   <li>形态 A = 应用<b>自己</b>引入一行依赖来管它自己要连的库；</li>
 *   <li>形态 B = 组织<b>一屏</b>看清全组织登记了哪些数据源、每个健康态如何、
 *       谁改过、谁用过 —— 但底座（模型 / 建池 / 探活 / 存储）<b>共用同一份
 *       {@code z-dms-core}</b>，不是两份代码。</li>
 * </ul>
 *
 * <p>本宿主只提供「管控面」：登记 / 编辑 / 摘除 / 测连接 / 变更历史 / 审计流水。
 * 真正的建池与探活动作全部委托给 starter 装出来的 {@code DataSourceService}。
 */
@SpringBootApplication
public class ZDmsAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZDmsAdminApplication.class, args);
    }
}
