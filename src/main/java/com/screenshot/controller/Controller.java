package com.screenshot.controller;

import com.screenshot.config.AppConfig;
import com.screenshot.config.ConfigManager;
import com.screenshot.exception.ControllerException;
import com.screenshot.exception.ScreenshotException;
import com.screenshot.engine.ScreenshotEngine;
import com.screenshot.hotkey.GlobalHotkeyManager;
import com.screenshot.scheduler.Archiver;
import com.screenshot.scheduler.Scheduler;
import com.screenshot.tray.TrayManager;
import com.screenshot.ui.SettingsDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 核心控制器
 * 负责协调各模块工作，管理应用状态
 *
 * @author wsj
 * @version 2.1.0
 */
public class Controller implements TrayManager.TrayCallback {
    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private AppState state;
    private ConfigManager configManager;
    private Scheduler scheduler;
    private ScreenshotEngine screenshotEngine;
    private TrayManager trayManager;

    /** 归档相关 */
    private Archiver archiver;
    private ScheduledExecutorService archiveExecutor;
    private ScheduledFuture<?> archiveFuture;

    /** 全局热键管理器 */
    private GlobalHotkeyManager hotkeyManager;

    /**
     * 创建控制器
     */
    public Controller() throws ControllerException {
        try {
            logger.info("初始化控制器");

            configManager = new ConfigManager(Paths.get("config.json"));
            state = new AppState(configManager.getConfig());

            screenshotEngine = new ScreenshotEngine(
                Paths.get(state.getConfig().getSavePath()),
                state.getConfig().getImageFormat()
            );

            AppConfig config = state.getConfig();
            scheduler = new Scheduler(
                config.getIdleInterval(),
                config.getBusyInterval(),
                config.getIdleThreshold()
            );

            archiver = new Archiver();
            archiveExecutor = Executors.newSingleThreadScheduledExecutor();

            // 初始化全局热键管理器
            initHotkey(config);

            logger.info("控制器初始化成功");

        } catch (Exception e) {
            logger.error("控制器初始化失败", e);
            throw new ControllerException("控制器初始化失败", e);
        }
    }

    /**
     * 启动截图功能
     */
    public void startCapture() {
        if (state.isRunning()) {
            logger.warn("截图功能已在运行中");
            return;
        }

        logger.info("启动截图功能（事件驱动模式）");
        state.setRunning(true);

        scheduler.start(new Runnable() {
            @Override
            public void run() {
                try {
                    logger.debug("执行定时截图");
                    screenshotEngine.capture();
                } catch (ScreenshotException e) {
                    logger.error("截图失败: {}", e.getMessage());
                }
            }
        });

        // 启动每日归档定时任务
        startArchiveTask();

        if (trayManager != null) {
            trayManager.updateMenuState(true);
        }
    }

    /**
     * 停止截图功能
     */
    public void stopCapture() {
        if (!state.isRunning()) {
            logger.warn("截图功能未运行");
            return;
        }

        logger.info("停止截图功能");
        state.setRunning(false);
        scheduler.stop();
        stopArchiveTask();

        if (trayManager != null) {
            trayManager.updateMenuState(false);
        }
    }

    // ========== 归档任务管理 ==========

    /**
     * 启动每日归档定时任务
     * 计算从现在到下次归档时间的延迟，然后每24小时执行一次
     */
    private void startArchiveTask() {
        stopArchiveTask();

        AppConfig config = state.getConfig();
        if (!config.isArchiveEnabled()) {
            logger.info("每日归档已禁用");
            return;
        }

        // 计算到下次归档时间的延迟
        long delaySeconds = calculateDelayToNextArchiveTime(config.getArchiveTime());

        logger.info("启动每日归档任务，归档时间: {}，距下次执行: {}秒（约{}小时）",
                config.getArchiveTime(), delaySeconds, delaySeconds / 3600);

        archiveFuture = archiveExecutor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    logger.info("执行每日归档任务");
                    int count = archiver.archive(Paths.get(state.getConfig().getSavePath()));
                    logger.info("归档完成，处理文件数: {}", count);
                } catch (Exception e) {
                    logger.error("归档任务执行异常", e);
                }
            }
        }, delaySeconds, 24 * 3600, TimeUnit.SECONDS);
    }

    /**
     * 停止归档定时任务
     */
    private void stopArchiveTask() {
        if (archiveFuture != null) {
            archiveFuture.cancel(false);
            archiveFuture = null;
        }
    }

    /**
     * 计算从现在到下次归档时间的延迟（秒）
     * 如果今天的归档时间已过，则延迟到明天同一时间
     *
     * @param archiveTimeStr HH:mm 格式的时间字符串
     * @return 延迟秒数
     */
    private long calculateDelayToNextArchiveTime(String archiveTimeStr) {
        try {
            LocalTime archiveTime = LocalTime.parse(archiveTimeStr, DateTimeFormatter.ofPattern("HH:mm"));
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime nextRun = LocalDateTime.of(LocalDate.now(), archiveTime);

            // 如果今天的归档时间已过，设为明天
            if (!nextRun.isAfter(now)) {
                nextRun = nextRun.plusDays(1);
            }

            return Duration.between(now, nextRun).getSeconds();
        } catch (DateTimeParseException e) {
            logger.warn("归档时间格式错误: {}，使用默认00:30", archiveTimeStr);
            return calculateDelayToNextArchiveTime("00:30");
        }
    }

    // ========== 配置管理 ==========

    /**
     * 更新配置
     */
    public void updateConfig(AppConfig newConfig) throws ControllerException {
        try {
            logger.info("更新配置");
            configManager.updateConfig(newConfig);
            state.setConfig(newConfig);

            screenshotEngine.setSavePath(Paths.get(newConfig.getSavePath()));
            screenshotEngine.setImageFormat(newConfig.getImageFormat());

            scheduler.setConfig(
                newConfig.getIdleInterval(),
                newConfig.getBusyInterval(),
                newConfig.getIdleThreshold()
            );

            // 更新热键
            updateHotkey(newConfig);

            // 如果正在运行，重启归档任务以应用新配置
            if (state.isRunning()) {
                startArchiveTask();
            }

        } catch (Exception e) {
            logger.error("配置更新失败", e);
            throw new ControllerException("配置更新失败", e);
        }
    }

    /**
     * 打开设置界面
     */
    public void openSettings() {
        logger.info("打开设置界面");

        try {
            AppConfig configCopy = new AppConfig();
            configCopy.setIdleInterval(state.getConfig().getIdleInterval());
            configCopy.setBusyInterval(state.getConfig().getBusyInterval());
            configCopy.setIdleThreshold(state.getConfig().getIdleThreshold());
            configCopy.setSavePath(state.getConfig().getSavePath());
            configCopy.setImageFormat(state.getConfig().getImageFormat());
            configCopy.setArchiveEnabled(state.getConfig().isArchiveEnabled());
            configCopy.setArchiveTime(state.getConfig().getArchiveTime());
            configCopy.setHotkeyEnabled(state.getConfig().isHotkeyEnabled());
            configCopy.setHotkey(state.getConfig().getHotkey());

            // 打开设置前注销全局热键，避免录制快捷键时触发截图
            if (hotkeyManager != null) {
                hotkeyManager.unregister();
            }

            SettingsDialog dialog = new SettingsDialog(null, configCopy);
            dialog.setVisible(true);

            if (dialog.isSaved()) {
                updateConfig(dialog.getConfig());
                logger.info("配置已更新");

                JOptionPane.showMessageDialog(null,
                    "配置已保存",
                    "成功",
                    JOptionPane.INFORMATION_MESSAGE);
            } else {
                // 用户取消，恢复原来的热键注册
                if (hotkeyManager != null) {
                    updateHotkey(state.getConfig());
                }
            }

        } catch (Exception e) {
            logger.error("打开设置界面失败", e);
            // 异常时也尝试恢复热键
            if (hotkeyManager != null) {
                updateHotkey(state.getConfig());
            }
            JOptionPane.showMessageDialog(null,
                "打开设置失败: " + e.getMessage(),
                "错误",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * 退出程序
     */
    public void exit() {
        logger.info("退出程序");
        stopCapture();
        scheduler.shutdown();

        // 关闭热键管理器
        if (hotkeyManager != null) {
            hotkeyManager.shutdown();
        }

        // 关闭归档调度器
        stopArchiveTask();
        archiveExecutor.shutdown();
        try {
            if (!archiveExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                archiveExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            archiveExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        if (trayManager != null) {
            trayManager.remove();
        }

        System.exit(0);
    }

    public void setTrayManager(TrayManager trayManager) {
        this.trayManager = trayManager;
    }

    public AppState getState() {
        return state;
    }

    // ========== 热键管理 ==========

    /**
     * 初始化全局热键管理器
     *
     * @param config 当前配置
     */
    private void initHotkey(AppConfig config) {
        hotkeyManager = new GlobalHotkeyManager();

        // 设置热键回调：任何状态下按快捷键立即截图
        hotkeyManager.setCallback(new GlobalHotkeyManager.HotkeyCallback() {
            @Override
            public void onHotkeyPressed() {
                try {
                    logger.info("热键触发手动截图");
                    screenshotEngine.capture();
                } catch (ScreenshotException e) {
                    logger.error("热键截图失败: {}", e.getMessage());
                }
            }
        });

        // 按配置注册热键
        if (config.isHotkeyEnabled() && config.getHotkey() != null) {
            boolean ok = hotkeyManager.register(config.getHotkey());
            if (!ok) {
                logger.warn("全局热键 {} 注册失败，可能已被其他程序占用", config.getHotkey());
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        JOptionPane.showMessageDialog(null,
                            "全局热键 " + config.getHotkey() + " 注册失败，\n可能已被其他程序占用。\n请在设置中更换快捷键。",
                            "热键注册失败",
                            JOptionPane.WARNING_MESSAGE);
                    }
                });
            }
        }
    }

    /**
     * 更新热键配置（配置变更时调用）
     *
     * @param config 新配置
     */
    private void updateHotkey(AppConfig config) {
        if (config.isHotkeyEnabled() && config.getHotkey() != null) {
            boolean ok = hotkeyManager.register(config.getHotkey());
            if (!ok) {
                logger.warn("全局热键 {} 注册失败，可能已被其他程序占用", config.getHotkey());
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        JOptionPane.showMessageDialog(null,
                            "全局热键 " + config.getHotkey() + " 注册失败，\n可能已被其他程序占用。\n请在设置中更换快捷键。",
                            "热键注册失败",
                            JOptionPane.WARNING_MESSAGE);
                    }
                });
            }
        } else {
            hotkeyManager.unregister();
            logger.info("全局热键已禁用");
        }
    }

    // ========== TrayCallback接口实现 ==========

    @Override
    public void onStart() { startCapture(); }

    @Override
    public void onStop() { stopCapture(); }

    @Override
    public void onSettings() { openSettings(); }

    @Override
    public void onExit() { exit(); }
}
