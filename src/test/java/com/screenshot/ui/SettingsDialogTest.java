package com.screenshot.ui;

import com.screenshot.config.AppConfig;
import com.screenshot.config.ImageFormat;
import com.screenshot.scheduler.UserActivityMonitor;

import javax.swing.*;

/**
 * 设置对话框测试
 *
 * @author wsj
 * @version 2.0.0
 */
public class SettingsDialogTest {

    public static void main(String[] args) {
        // 设置外观
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 创建测试配置
        AppConfig config = new AppConfig();
        config.setIdleInterval(300);
        config.setBusyInterval(60);
        config.setIdleThreshold(UserActivityMonitor.DEFAULT_IDLE_THRESHOLD_SECONDS);
        config.setSavePath("./screenshots");
        config.setImageFormat(ImageFormat.PNG);

        // 显示设置对话框
        SettingsDialog dialog = new SettingsDialog(null, config);
        dialog.setVisible(true);

        // 输出结果
        if (dialog.isSaved()) {
            System.out.println("配置已保存:");
            System.out.println("  空闲间隔: " + dialog.getConfig().getIdleInterval() + "秒");
            System.out.println("  忙碌间隔: " + dialog.getConfig().getBusyInterval() + "秒");
            System.out.println("  空闲阈值: " + dialog.getConfig().getIdleThreshold() + "秒");
            System.out.println("  路径: " + dialog.getConfig().getSavePath());
            System.out.println("  格式: " + dialog.getConfig().getImageFormat());
        } else {
            System.out.println("用户取消");
        }

        System.exit(0);
    }
}
