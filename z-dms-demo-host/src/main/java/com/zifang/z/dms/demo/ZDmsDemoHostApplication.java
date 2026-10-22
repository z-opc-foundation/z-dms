package com.zifang.z.dms.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 形态 A 宿主样例。
 * <p>
 * <b>它证明的事</b>：宿主只加一行 {@code z-dms-spring-boot-starter} 依赖 +
 * 一个 {@code @SpringBootApplication}，就拿到了：数据源登记（CRUD + MySQL 存储）、
 * Druid 连接工厂（注册即探活）、本地缓存 + z-config 推送通道、定时探活、宿主可见的健康面。
 * <p>
 * 宿主自己的 {@code /health} 直接并上 z-dms 的健康报告 ⇒ 探活失败在宿主健康端点上看得见。
 */
@SpringBootApplication
public class ZDmsDemoHostApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZDmsDemoHostApplication.class, args);
    }
}
