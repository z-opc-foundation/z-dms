package com.zifang.z.dms.starter.health;

import com.zifang.z.dms.core.model.DataSourceHealthReport;
import com.zifang.z.dms.core.model.DataSourceProbeResult;
import com.zifang.z.dms.core.service.DataSourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 定时探活（卡面：「定时探活 → 失败上报」）。
 * <p>
 * <b>失败上报走两条腿</b>：
 * <ol>
 *   <li><b>日志</b>：ERROR 级别打出来，带数据源 id 与失败原因（不含任何凭据）；</li>
 *   <li><b>健康面</b>：最近一次报告缓存在 {@link #lastReport()}，宿主 /health 与 actuator 直接读它。</li>
 * </ol>
 * 用独立调度线程而不是 {@code @Scheduled}：不要求宿主开 {@code @EnableScheduling}，
 * 也不跟宿主自己的调度池抢线程。
 */
public class DataSourceHealthScheduler {

    private static final Logger log = LoggerFactory.getLogger(DataSourceHealthScheduler.class);

    private final DataSourceService dataSourceService;
    private final long intervalMs;
    private final ScheduledExecutorService scheduler;
    private final AtomicReference<DataSourceHealthReport> lastReport = new AtomicReference<>();

    public DataSourceHealthScheduler(DataSourceService dataSourceService, long intervalMs) {
        this.dataSourceService = dataSourceService;
        this.intervalMs = intervalMs;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "dms-health-scheduler");
                t.setDaemon(true);
                return t;
            }
        });
    }

    public void start() {
        scheduler.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                try {
                    probeOnce();
                } catch (RuntimeException e) {
                    log.error("z-dms 定时探活异常: {}", e.getMessage(), e);
                }
            }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        log.info("z-dms 定时探活已启动，间隔 {}ms", intervalMs);
    }

    public void stop() {
        scheduler.shutdownNow();
    }

    /** 探活一轮（也供测试与宿主手动触发）。 */
    public DataSourceHealthReport probeOnce() {
        DataSourceHealthReport report = dataSourceService.health();
        lastReport.set(report);
        if (report.isUp()) {
            log.debug("z-dms 定时探活：全部 UP（{} 个源）", report.getSources().size());
        } else {
            for (DataSourceProbeResult r : report.getSources()) {
                if (!r.isUp()) {
                    log.error("z-dms 探活失败：数据源 [{}] 状态 DOWN —— {}", r.getId(), r.getDetail());
                }
            }
            log.error("z-dms 整体健康状态 DOWN：{}", report.getReason());
        }
        return report;
    }

    /** 最近一次探活报告（没有则现探一次）。 */
    public DataSourceHealthReport lastReport() {
        DataSourceHealthReport report = lastReport.get();
        return report == null ? dataSourceService.health() : report;
    }
}
