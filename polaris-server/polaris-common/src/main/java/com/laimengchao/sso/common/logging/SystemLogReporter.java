package com.laimengchao.sso.common.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * 系统日志异步批量上报器（公共层，随 polaris-common 进入所有服务）。
 * <p>
 * 设计约定：
 * <ul>
 *   <li>Appender 只做「入队」，HTTP 发送在独立守护线程，绝不阻塞业务线程；</li>
 *   <li>队列满即丢并计数（System.err 限频告警），绝不让日志拖垮应用；</li>
 *   <li>发送失败只告警，不回传异常——运行日志不能影响业务；</li>
 *   <li>上报线程打日志会再次触发 Appender，内部一律不用 slf4j，用 stderr 限频；</li>
 *   <li>正文做口令/令牌脱敏与长度截断，避免敏感信息与超大对象入库。</li>
 * </ul>
 */
public class SystemLogReporter {

    /** 敏感字段脱敏：key=value / key: value 形式，覆盖常见 token/password 写法 */
    private static final Pattern SECRET = Pattern.compile(
            "(?i)(password|passwd|pwd|token|secret|authorization|access[_-]?key|secret[_-]?key)"
                    + "\\s*[=:]\\s*([^\\s,;}\"']+)", Pattern.MULTILINE);

    private static final int MAX_MESSAGE = 4000;
    private static final int MAX_STACK = 4000;

    private final ObjectMapper mapper = new ObjectMapper();
    private final BlockingQueue<SystemLogEvent> queue;
    private final String appName;
    private final HttpClient httpClient;
    private final URI endpoint;
    private final String internalToken;
    private final int batchSize;
    private final long flushMs;
    private final AtomicLong sent = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong lastWarnAt = new AtomicLong();

    private final Thread worker;
    private volatile boolean running = true;

    public SystemLogReporter(String app, String baseUrl, String internalToken,
                             int queueSize, int batchSize, long flushMs) {
        this.appName = app == null || app.isBlank() ? "unknown" : app.trim();
        this.internalToken = internalToken == null ? "" : internalToken.trim();
        this.batchSize = Math.max(1, batchSize);
        this.flushMs = Math.max(100, flushMs);
        this.queue = new ArrayBlockingQueue<>(Math.max(100, queueSize));
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        this.endpoint = URI.create(baseUrl.replaceAll("/+$", "") + "/internal/logs");
        this.worker = new Thread(this::drainLoop, "system-log-reporter");
        this.worker.setDaemon(true);
        this.worker.start();
    }

    /** Appender 调用：非阻塞入队，队列满则丢弃。 */
    public void offer(SystemLogEvent event) {
        if (event.getApp() == null || event.getApp().isBlank()) {
            event.setApp(appName);
        }
        if (!queue.offer(event)) {
            long d = dropped.incrementAndGet();
            warnThrottled("系统日志队列已满，丢弃一条（累计 " + d + "）");
        }
    }

    public long sentCount() {
        return sent.get();
    }

    public long droppedCount() {
        return dropped.get();
    }

    public long failedCount() {
        return failed.get();
    }

    /** 日志正文脱敏 + 截断；异常堆栈截断。 */
    public static String mask(String text, int max) {
        if (text == null) return "";
        String s = SECRET.matcher(text).replaceAll("$1=***");
        return s.length() <= max ? s : s.substring(0, max) + "...(truncated)";
    }

    public static int maxMessage() {
        return MAX_MESSAGE;
    }

    public static int maxStack() {
        return MAX_STACK;
    }

    private void drainLoop() {
        List<SystemLogEvent> batch = new ArrayList<>(batchSize);
        while (running) {
            try {
                SystemLogEvent first = queue.poll(flushMs, TimeUnit.MILLISECONDS);
                if (first == null) {
                    if (!batch.isEmpty()) flush(batch);
                    continue;
                }
                batch.add(first);
                queue.drainTo(batch, batchSize - 1);
                flush(batch);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable t) {
                warnThrottled("系统日志上报线程异常（已忽略）: " + t.getMessage());
            }
        }
        if (!batch.isEmpty()) flush(batch);
    }

    private void flush(List<SystemLogEvent> batch) {
        if (batch.isEmpty()) return;
        try {
            String json = mapper.writeValueAsString(batch);
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(3))
                    .header("Content-Type", "application/json")
                    .header("X-Internal-Token", internalToken)
                    .POST(HttpRequest.BodyPublishers.ofString(json, java.nio.charset.StandardCharsets.UTF_8))
                    .build();
            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            sent.addAndGet(batch.size());
        } catch (Throwable t) {
            failed.addAndGet(batch.size());
            warnThrottled("系统日志上报失败（已忽略，不影响业务）: " + t.getMessage());
        } finally {
            batch.clear();
        }
    }

    /** 限频告警：30 秒内只打一次，且用 stderr，绝不回到 logback 触发递归。 */
    private void warnThrottled(String message) {
        long now = System.currentTimeMillis();
        long prev = lastWarnAt.get();
        if (now - prev >= 30_000 && lastWarnAt.compareAndSet(prev, now)) {
            System.err.println("[system-log] " + message);
        }
    }

    @PreDestroy
    public void shutdown() {
        running = false;
        worker.interrupt();
        List<SystemLogEvent> rest = new ArrayList<>();
        queue.drainTo(rest);
        if (!rest.isEmpty()) flush(rest);
    }
}
