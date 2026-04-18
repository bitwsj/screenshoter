package com.screenshot.scheduler;

import com.sun.jna.Native;
import com.sun.jna.Structure;
import com.sun.jna.win32.StdCallLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 用户活动监控器
 * 通过Windows API GetLastInputInfo检测用户空闲时间
 * 支持后台监控线程，状态切换时触发回调
 *
 * 状态模型：
 * - 空闲 → 忙碌：检测到键鼠操作（idleTime < threshold）
 * - 忙碌 → 空闲：最后一次键鼠操作后，超过 idleThresholdSeconds 无操作
 *
 * @author wsj
 * @version 2.0.0
 */
public class UserActivityMonitor {
    private static final Logger logger = LoggerFactory.getLogger(UserActivityMonitor.class);

    /** 默认空闲判定阈值（秒） */
    public static final long DEFAULT_IDLE_THRESHOLD_SECONDS = 120;

    /** 监控线程轮询间隔（毫秒） */
    private static final long POLL_INTERVAL_MS = 1000;

    /** 空闲判定阈值（毫秒） */
    private volatile long idleThresholdMs;

    /** 当前是否空闲 */
    private final AtomicBoolean idle = new AtomicBoolean(true);

    /** 状态切换回调 */
    public interface StateChangeListener {
        /**
         * 状态切换时调用
         *
         * @param nowIdle true=切换到空闲，false=切换到忙碌
         */
        void onStateChanged(boolean nowIdle);
    }

    /** 回调引用 */
    private volatile StateChangeListener listener;

    /** 监控线程 */
    private Thread monitorThread;

    /** 控制监控线程运行 */
    private final AtomicBoolean monitoring = new AtomicBoolean(false);

    /**
     * Windows user32.dll 接口
     * GetLastInputInfo 位于 user32.dll
     */
    public interface User32 extends StdCallLibrary {
        User32 INSTANCE = Native.load("user32", User32.class);

        boolean GetLastInputInfo(LASTINPUTINFO plii);
    }

    /**
     * Windows kernel32.dll 接口
     * GetTickCount 位于 kernel32.dll
     */
    public interface Kernel32 extends StdCallLibrary {
        Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class);

        int GetTickCount();
    }

    /**
     * LASTINPUTINFO 结构体，对应Windows API中的同结构
     */
    public static class LASTINPUTINFO extends Structure {
        public int cbSize;
        public int dwTime;

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("cbSize", "dwTime");
        }
    }

    /**
     * 创建用户活动监控器
     *
     * @param idleThresholdSeconds 空闲判定阈值（秒），无键鼠操作超过此时间判定为空闲
     */
    public UserActivityMonitor(long idleThresholdSeconds) {
        this.idleThresholdMs = idleThresholdSeconds * 1000;
    }

    /**
     * 设置状态切换回调
     *
     * @param listener 回调
     */
    public void setListener(StateChangeListener listener) {
        this.listener = listener;
    }

    /**
     * 启动后台监控线程
     */
    public void startMonitoring() {
        if (monitoring.compareAndSet(false, true)) {
            // 初始化状态
            long idleTime = getIdleTimeMillis();
            idle.set(idleTime >= idleThresholdMs);
            logger.info("启动活动监控，初始状态: {}，空闲阈值: {}秒",
                    idle.get() ? "空闲" : "忙碌", idleThresholdMs / 1000);

            monitorThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (monitoring.get()) {
                        try {
                            Thread.sleep(POLL_INTERVAL_MS);
                            checkStateChange();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }, "ActivityMonitor");
            monitorThread.setDaemon(true);
            monitorThread.start();
        }
    }

    /**
     * 停止后台监控线程
     */
    public void stopMonitoring() {
        if (monitoring.compareAndSet(true, false)) {
            if (monitorThread != null) {
                monitorThread.interrupt();
                monitorThread = null;
            }
            logger.info("停止活动监控");
        }
    }

    /**
     * 检查状态是否发生变化，如果变化则通知回调
     */
    private void checkStateChange() {
        long idleTime = getIdleTimeMillis();
        boolean nowIdle = idleTime >= idleThresholdMs;
        boolean wasIdle = idle.getAndSet(nowIdle);

        if (wasIdle != nowIdle) {
            if (nowIdle) {
                logger.info("状态切换: 忙碌 → 空闲（无键鼠操作已达{}秒）", idleThresholdMs / 1000);
            } else {
                logger.info("状态切换: 空闲 → 忙碌（检测到键鼠操作）");
            }

            StateChangeListener l = listener;
            if (l != null) {
                l.onStateChanged(nowIdle);
            }
        }
    }

    /**
     * 获取用户空闲时间（毫秒）
     *
     * @return 空闲时间（毫秒），如果获取失败返回0
     */
    public long getIdleTimeMillis() {
        try {
            LASTINPUTINFO lastInputInfo = new LASTINPUTINFO();
            lastInputInfo.cbSize = lastInputInfo.size();

            if (User32.INSTANCE.GetLastInputInfo(lastInputInfo)) {
                int tickCount = Kernel32.INSTANCE.GetTickCount();
                int idleMillis = tickCount - lastInputInfo.dwTime;
                return idleMillis & 0xFFFFFFFFL;
            }
        } catch (UnsatisfiedLinkError e) {
            logger.warn("无法加载Windows DLL，空闲检测不可用: {}", e.getMessage());
        } catch (Exception e) {
            logger.warn("获取空闲时间失败: {}", e.getMessage());
        }
        return 0;
    }

    /**
     * 获取当前是否空闲
     *
     * @return true=空闲
     */
    public boolean isIdle() {
        return idle.get();
    }

    /**
     * 更新空闲判定阈值
     *
     * @param thresholdSeconds 新的阈值（秒）
     */
    public void setIdleThreshold(long thresholdSeconds) {
        this.idleThresholdMs = thresholdSeconds * 1000;
        logger.info("空闲阈值已更新为: {}秒", thresholdSeconds);
    }

    /**
     * 获取空闲判定阈值（秒）
     *
     * @return 阈值秒数
     */
    public long getIdleThresholdSeconds() {
        return idleThresholdMs / 1000;
    }
}
