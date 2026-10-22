package com.zifang.z.dms.admin.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * SPA history fallback。
 *
 * <p>前端是 BrowserRouter，刷新 {@code /z-dms/sources} 这类路径时 Spring 找不到静态资源
 * 就返回白屏 404（**实测过**：jar 里只有 {@code index.html} 与 {@code /assets/**}，
 * 深层路由不在静态资源里 ⇒ 直接 404）。
 * vite dev / preview 自带 fallback，本地永远看不见，只在 jar 里现形。
 *
 * <p>⚠️ **不能写成 {@code /**}**：那会把 {@code /api/**} 的 404 也 forward 过去，
 * 把「接口不存在」伪造成「返回了一坨 HTML」，客户端 JSON.parse 直接炸。
 * 逐条列前端路由前缀（形态 B 的前端路由都在 {@code /z-dms/**} 下，
 * 后端接口都在 {@code /api/dms/**} 下，两者不重叠）。
 */
@Configuration
public class DmsWebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/z-dms/identity").setViewName("forward:/index.html");
        registry.addViewController("/z-dms/**").setViewName("forward:/index.html");
    }
}
