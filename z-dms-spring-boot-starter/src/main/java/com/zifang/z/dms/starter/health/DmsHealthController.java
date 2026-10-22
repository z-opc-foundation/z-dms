package com.zifang.z.dms.starter.health;

import com.zifang.z.dms.core.service.DataSourceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 宿主可见的健康端点：<b>探活失败在这里看得见，不静默</b>。
 * <p>
 * 形状与 z-lc 的 {@code /api/lc/health} 的 sources 段一致：
 * {@code {status, sources:[{id,name,status,detail,latencyMs}], reason}}，
 * 且 {@code status} 只有「至少一个池且全部 UP」才是 UP。
 * <p>
 * 路径可用 {@code z.dms.health-path} 改；宿主也可以在自己的 {@code /health} 里并这份内容。
 */
@RestController
@RequestMapping("${z.dms.health-path:/dms/health}")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class DmsHealthController {

    private final DataSourceService dataSourceService;

    @Autowired
    public DmsHealthController(DataSourceService dataSourceService) {
        this.dataSourceService = dataSourceService;
    }

    @GetMapping
    public Map<String, Object> health() {
        return DmsHealthView.toMap(dataSourceService.health());
    }
}
