package com.zifang.z.dms.starter.event;

import com.zifang.z.dms.core.event.DataSourceChangeEvent;
import com.zifang.z.dms.core.event.DataSourceChangePublisher;

/**
 * 发布方向：把变更事件转成 z-config 推送（由 bridge 执行实际推送）。
 */
public class ZConfigDataSourceChangePublisher implements DataSourceChangePublisher {

    private final ZConfigDataSourceChangeBridge bridge;

    public ZConfigDataSourceChangePublisher(ZConfigDataSourceChangeBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public void publish(DataSourceChangeEvent event) {
        bridge.publishCurrent();
    }
}
