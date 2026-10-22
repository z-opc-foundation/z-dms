package com.zifang.z.dms.core.event;

/**
 * 变更发布器（SPI）：把本进程的定义变更推到组织级通道（形态 A 里是 z-config 长连接）。
 * <p>
 * core 不认识 z-config —— 它只认这个接口；starter 负责把 z-config 客户端接上来。
 */
public interface DataSourceChangePublisher {

    void publish(DataSourceChangeEvent event);
}
