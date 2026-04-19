package com.screenshot.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 定时调度器（事件驱动 + 自调度模型）
 * 根据用户空闲/忙碌状态动态调整截图频率
 *
 * 调度策略：
 * 1. 状态切换（空闲↔忙碌）时：
 *    - 立即取消当前等待中的截图任务
 *    - 立即执行一次截图
 *    - 按新状态的间隔重新开始自调度
 * 2. 无状态变化时：
 *    - 按当前状态对应间隔正常执行截图
 *
 * 线程安全：
 * 所有对 executor 的调度操作（scheduleNext/cancel+reschedule）统一通过
 * executor 线程串行执行，避免 Monitor 线程与 Executor 线程的并发竞态。
 *
 * @author wsj
 * @version 2.1.0
 */
public class Scheduler implements UserActivityMonitor.StateChangeListener {
    private static final Logger logger = LoggerFactory.getLogger(Scheduler.class);

    /** 空闲时段截图间隔（秒） */
    private volatile long idleIntervalSeconds;

    /** 忙碌时段截图间隔（秒） */
    private volatile long busyIntervalSeconds;

    private ScheduledExecutorService executor;

    /** 当前等待中的截图任务，volatile 保证跨线程可见性 */
    private volatile ScheduledFuture<?> scheduledFuture;

    /** 用户活动监控器 */
    private UserActivityMonitor activityMonitor;

    /** 截图任务（外部传入） */
    private Runnable task;

    /** 是否正在运行 */
    private volatile boolean running = false;

    /**
     * 创建调度器
     *
     * @param idleIntervalSeconds  空闲时段截图间隔（秒）
     * @param busyIntervalSeconds  忙碌时段截图间隔（秒）
     * @param idleThresholdSeconds 空闲判定阈值（秒），无键鼠操作超过此时间判定为空闲
     */
    public Scheduler(long idleIntervalSeconds, long busyIntervalSeconds, long idleThresholdSeconds) {
        this.idleIntervalSeconds = idleIntervalSeconds;
        this.busyIntervalSeconds = busyIntervalSeconds;
        this.executor = Executors.newSingleThreadScheduledExecutor();
        this.activityMonitor = new UserActivityMonitor(idleThresholdSeconds);
        this.activityMonitor.setListener(this);
        logger.info("调度器创建成功，空闲截图间隔: {}秒，忙碌截图间隔: {}秒，空闲阈值: {}秒",
                idleIntervalSeconds, busyIntervalSeconds, idleThresholdSeconds);
    }

    /**
     * 启动定时任务
     *
     * @param task 要执行的截图任务
     */
    public void start(Runnable task) {
        if (running) {
            logger.warn("定时任务已在运行中");
            return;
        }

        this.task = task;
        this.running = true;

        // 启动活动监控（后台线程检测状态切换，触发回调）
        activityMonitor.startMonitoring();

        logger.info("启动截图任务（事件驱动模式），初始状态: {}",
                activityMonitor.isIdle() ? "空闲" : "忙碌");

        // 立即执行第一次截图
        executeScreenshot();

        // 按当前状态对应间隔调度下一次
        scheduleNext();
    }

    /**
     * 按当前状态间隔调度下一次截图
     * 仅在 executor 线程上调用（或在 start 初始化时由调用方线程调用一次）
     */
    private void scheduleNext() {
        if (!running) {
            return;
        }

        boolean idle = activityMonitor.isIdle();
        long interval = idle ? idleIntervalSeconds : busyIntervalSeconds;

        logger.debug("调度下次截图: {}秒后（当前状态: {}）", interval, idle ? "空闲" : "忙碌");

        scheduledFuture = executor.schedule(new Runnable() {
            @Override
            public void run() {
                if (!running) {
                    return;
                }
                executeScreenshot();
                scheduleNext();
            }
        }, interval, TimeUnit.SECONDS);
    }

    /**
     * 执行一次截图
     */
    private void executeScreenshot() {
        try {
            logger.debug("执行截图");
            task.run();
        } catch (Exception e) {
            logger.error("截图任务执行异常: {}", e.getMessage());
        }
    }

    // ========== StateChangeListener 回调实现 ==========

    /**
     * 状态切换回调（由 ActivityMonitor 的 daemon 线程调用）
     *
     * 关键设计：将 cancel + 截图 + 重新调度 操作提交到 executor 执行，
     * 确保与 executor 线程上正在运行的 scheduleNext() 串行化，
     * 避免并发产生孤儿任务。
     */
    @Override
    public void onStateChanged(boolean nowIdle) {
        if (!running) {
            return;
        }

        logger.info("检测到状态切换 → {}，提交重调度任务到执行器",
                nowIdle ? "空闲" : "忙碌");

        // 提交到 executor 线程执行，保证与 scheduleNext() 串行
        executor.submit(new Runnable() {
            @Override
            public void run() {
                if (!running) {
                    return;
                }

                // 取消当前等待中的截图任务
                if (scheduledFuture != null) {
                    scheduledFuture.cancel(false);
                    scheduledFuture = null;
                }

                // 状态切换时立即截图
                logger.info("状态切换触发立即截图，新状态: {}，后续间隔: {}秒",
                        nowIdle ? "空闲" : "忙碌",
                        nowIdle ? idleIntervalSeconds : busyIntervalSeconds);
                executeScreenshot();

                // 按新状态间隔重新调度
                scheduleNext();
            }
        });
    }

    /**
     * 停止定时任务
     */
    public void stop() {
        running = false;
        activityMonitor.stopMonitoring();

        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
            scheduledFuture = null;
        }
        logger.info("停止截图任务");
    }

    /**
     * 更新间隔时间和空闲阈值
     *
     * @param idleIntervalSeconds  空闲时段截图间隔（秒）
     * @param busyIntervalSeconds  忙碌时段截图间隔（秒）
     * @param idleThresholdSeconds 空闲判定阈值（秒）
     */
    public void setConfig(long idleIntervalSeconds, long busyIntervalSeconds, long idleThresholdSeconds) {
        this.idleIntervalSeconds = idleIntervalSeconds;
        this.busyIntervalSeconds = busyIntervalSeconds;
        this.activityMonitor.setIdleThreshold(idleThresholdSeconds);
        logger.info("更新配置 - 空闲截图间隔: {}秒, 忙碌截图间隔: {}秒, 空闲阈值: {}秒",
                idleIntervalSeconds, busyIntervalSeconds, idleThresholdSeconds);
    }

    /**
     * 获取当前空闲时段截图间隔
     */
    public long getIdleInterval() {
        return idleIntervalSeconds;
    }

    /**
     * 获取当前忙碌时段截图间隔
     */
    public long getBusyInterval() {
        return busyIntervalSeconds;
    }

    /**
     * 获取当前空闲判定阈值（秒）
     */
    public long getIdleThreshold() {
        return activityMonitor.getIdleThresholdSeconds();
    }

    /**
     * 检查任务是否正在运行
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * 关闭调度器，释放所有资源
     */
    public void shutdown() {
        logger.info("关闭调度器");
        stop();
        executor.shutdown();

        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
