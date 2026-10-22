package com.zifang.z.dms.core.health;

import com.zifang.z.dms.core.model.DataSourceHealthReport;
import com.zifang.z.dms.core.model.DataSourceProbeResult;
import com.zifang.z.dms.core.pool.DataSourcePool;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 逐池真探活。
 * <p>
 * <b>口径抄 z-lc {@code DataSourceHealthProber}</b>（同款超时、同款线程隔离、同款报告形状）：
 * <ul>
 *   <li>探活跑在独立线程 + 超时：池的 maxWait 可能是 -1（无限等），
 *       直接在请求线程里 getConnection() 会让健康检查自己挂死，那比报 DOWN 糟得多；</li>
 *   <li>探活失败必须<b>显式落到报告里</b>（status=DOWN + detail），不允许静默吞掉；</li>
 *   <li>整体 status 只有「至少一个池且全部 UP」才是 UP。</li>
 * </ul>
 */
public class DataSourceHealthProber {

    private static final long DEFAULT_TIMEOUT_MS = 2000L;
    private static final int DETAIL_MAX_LEN = 200;

    private final long timeoutMs;
    private final ExecutorService executor;

    public DataSourceHealthProber() {
        this(DEFAULT_TIMEOUT_MS);
    }

    public DataSourceHealthProber(final long timeoutMs) {
        this.timeoutMs = timeoutMs;
        this.executor = Executors.newCachedThreadPool(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "dms-health-probe");
                t.setDaemon(true);
                return t;
            }
        });
    }

    /** 批量探活，产出整体报告。 */
    public DataSourceHealthReport report(Collection<DataSourcePool> pools) {
        List<DataSourceProbeResult> results = new ArrayList<>();
        if (pools != null) {
            for (DataSourcePool pool : pools) {
                results.add(probe(pool));
            }
        }
        return DataSourceHealthReport.of(results);
    }

    /** 单池探活。 */
    public DataSourceProbeResult probe(final DataSourcePool pool) {
        final String id = pool.definition().getId();
        final String name = pool.definition().getName();
        Future<DataSourceProbeResult> future = null;
        try {
            future = executor.submit(() -> connect(pool, id, name));
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            if (future != null) {
                future.cancel(true);
            }
            // 超时只说明「池里借不到连接」；再直连一次把根因（Connection refused / 认证失败）挖出来。
            String root = safeDiagnose(pool);
            return DataSourceProbeResult.down(id, name,
                    "probe timed out after " + timeoutMs + "ms (池可能借干了或 maxWait=-1 在无限等)"
                            + (root == null ? "" : "；直连诊断: " + root));
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause() == null ? ee : ee.getCause();
            String root = safeDiagnose(pool);
            return DataSourceProbeResult.down(id, name,
                    cause.getClass().getSimpleName() + ": " + brief(cause.getMessage())
                            + (root == null ? "" : "；直连诊断: " + root));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return DataSourceProbeResult.down(id, name, "probe interrupted");
        } catch (RejectedExecutionException re) {
            return DataSourceProbeResult.down(id, name, "probe executor rejected: " + brief(re.getMessage()));
        }
    }

    private DataSourceProbeResult connect(DataSourcePool pool, String id, String name) {        long startNs = System.nanoTime();
        try (Connection conn = pool.getConnection()) {
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT 1")) {
                if (!rs.next()) {
                    throw new IllegalStateException("SELECT 1 没有返回任何行");
                }
            }
            long latencyMs = (System.nanoTime() - startNs) / 1_000_000L;
            return DataSourceProbeResult.up(id, name, "SELECT 1 ok", latencyMs);
        } catch (Exception e) {
            return DataSourceProbeResult.down(id, name,
                    e.getClass().getSimpleName() + ": " + brief(e.getMessage()));
        }
    }

    private String brief(String message) {
        return brief(message, DETAIL_MAX_LEN);
    }

    /** 诊断线索拿不到就返回 null（诊断本身失败不能影响探活结论）。 */
    private String safeDiagnose(DataSourcePool pool) {
        try {
            String d = pool.diagnose();
            return d == null || d.trim().isEmpty() ? null : d;
        } catch (Exception e) {
            return null;
        }
    }

    private static String brief(String value, int max) {
        if (value == null) {
            return "";
        }
        String flat = value.replace('\n', ' ').replace('\r', ' ').trim();
        return flat.length() <= max ? flat : flat.substring(0, max) + "…";
    }

    /** 停机时收掉探活线程池。 */
    public void shutdown() {
        executor.shutdownNow();
    }
}
