package com.screenshot.controller;

import com.screenshot.config.AppConfig;
import com.screenshot.config.ConfigManager;
import com.screenshot.exception.ControllerException;
import com.screenshot.exception.ScreenshotException;
import com.screenshot.engine.ScreenshotEngine;
import com.screenshot.scheduler.Scheduler;
import com.screenshot.tray.TrayManager;
import com.screenshot.ui.SettingsDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.nio.file.Paths;

/**
 * 核心控制器
 * 负责协调各模块工作，管理应用状态
 *
 * @author wsj
 * @version 2.0.0
 */
public class Controller implements TrayManager.TrayCallback {
    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private AppState state;
    private ConfigManager configManager;
    private Scheduler scheduler;
    private ScreenshotEngine screenshotEngine;
    private TrayManager trayManager;

    /**
     * 创建控制器
     *
     * @throws ControllerException 创建失败时抛出异常
     */
    public Controller() throws ControllerException {
        try {
            logger.info("初始化控制器");

            // 初始化配置管理器
            configManager = new ConfigManager(Paths.get("config.json"));

            // 初始化应用状态
            state = new AppState(configManager.getConfig());

            // 初始化截图引擎
            screenshotEngine = new ScreenshotEngine(
                Paths.get(state.getConfig().getSavePath()),
                state.getConfig().getImageFormat()
            );

            // 初始化调度器（三参数：空闲间隔、忙碌间隔、空闲阈值）
            AppConfig config = state.getConfig();
            scheduler = new Scheduler(
                config.getIdleInterval(),
                config.getBusyInterval(),
                config.getIdleThreshold()
            );

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

        // 启动定时任务
        scheduler.start(new Runnable() {
            @Override
            public void run() {
                try {
                    logger.debug("执行定时截图");
                    screenshotEngine.capture();
                } catch (ScreenshotException e) {
                    // 静默记录错误，继续运行
                    logger.error("截图失败: {}", e.getMessage());
                }
            }
        });

        // 更新托盘状态
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

        // 更新托盘状态
        if (trayManager != null) {
            trayManager.updateMenuState(false);
        }
    }

    /**
     * 更新配置
     *
     * @param newConfig 新配置
     * @throws ControllerException 更新失败时抛出异常
     */
    public void updateConfig(AppConfig newConfig) throws ControllerException {
        try {
            logger.info("更新配置");
            configManager.updateConfig(newConfig);
            state.setConfig(newConfig);

            // 更新截图引擎
            screenshotEngine.setSavePath(Paths.get(newConfig.getSavePath()));
            screenshotEngine.setImageFormat(newConfig.getImageFormat());

            // 更新调度器配置（间隔和阈值，动态生效无需重启）
            scheduler.setConfig(
                newConfig.getIdleInterval(),
                newConfig.getBusyInterval(),
                newConfig.getIdleThreshold()
            );

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
            // 创建配置副本
            AppConfig configCopy = new AppConfig();
            configCopy.setIdleInterval(state.getConfig().getIdleInterval());
            configCopy.setBusyInterval(state.getConfig().getBusyInterval());
            configCopy.setIdleThreshold(state.getConfig().getIdleThreshold());
            configCopy.setSavePath(state.getConfig().getSavePath());
            configCopy.setImageFormat(state.getConfig().getImageFormat());

            // 显示设置对话框
            SettingsDialog dialog = new SettingsDialog(null, configCopy);
            dialog.setVisible(true);

            // 如果用户保存了配置
            if (dialog.isSaved()) {
                updateConfig(dialog.getConfig());
                logger.info("配置已更新");

                // 显示成功提示
                JOptionPane.showMessageDialog(null,
                    "配置已保存",
                    "成功",
                    JOptionPane.INFORMATION_MESSAGE);
            }

        } catch (Exception e) {
            logger.error("打开设置界面失败", e);
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

        // 停止截图
        stopCapture();

        // 关闭调度器
        scheduler.shutdown();

        // 移除托盘图标
        if (trayManager != null) {
            trayManager.remove();
        }

        // 退出
        System.exit(0);
    }

    /**
     * 设置托盘管理器
     *
     * @param trayManager 托盘管理器
     */
    public void setTrayManager(TrayManager trayManager) {
        this.trayManager = trayManager;
    }

    /**
     * 获取当前状态
     *
     * @return 应用状态
     */
    public AppState getState() {
        return state;
    }

    // ========== TrayCallback接口实现 ==========

    @Override
    public void onStart() {
        startCapture();
    }

    @Override
    public void onStop() {
        stopCapture();
    }

    @Override
    public void onSettings() {
        openSettings();
    }

    @Override
    public void onExit() {
        exit();
    }
}
