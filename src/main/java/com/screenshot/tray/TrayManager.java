package com.screenshot.tray;

import com.screenshot.exception.TrayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.InputStream;

/**
 * 系统托盘管理器
 * 负责系统托盘图标和菜单的管理
 * 
 * @author wsj
 * @version 1.0.0
 */
public class TrayManager {
    private static final Logger logger = LoggerFactory.getLogger(TrayManager.class);
    
    private SystemTray systemTray;
    private TrayIcon trayIcon;
    private PopupMenu popupMenu;
    private MenuItem startItem;
    private MenuItem stopItem;
    private MenuItem settingsItem;
    private MenuItem exitItem;
    
    // 回调接口
    private TrayCallback callback;
    
    /**
     * 托盘回调接口
     */
    public interface TrayCallback {
        void onStart();
        void onStop();
        void onSettings();
        void onExit();
    }
    
    /**
     * 创建托盘管理器
     * 
     * @param callback 回调接口
     * @throws TrayException 创建失败时抛出异常
     */
    public TrayManager(TrayCallback callback) throws TrayException {
        if (!SystemTray.isSupported()) {
            throw new TrayException("系统托盘不支持");
        }
        
        this.callback = callback;
        this.systemTray = SystemTray.getSystemTray();
        
        // 创建菜单
        buildMenu();
        
        // 创建托盘图标
        Image image = loadIcon("/screenshot_stopped.png");
        trayIcon = new TrayIcon(image, "截图工具", popupMenu);
        trayIcon.setImageAutoSize(true);
        
        try {
            systemTray.add(trayIcon);
            logger.info("托盘图标创建成功");
        } catch (AWTException e) {
            logger.error("托盘图标添加失败", e);
            throw new TrayException("托盘图标添加失败", e);
        }
    }
    
    /**
     * 构建菜单
     */
    private void buildMenu() {
        popupMenu = new PopupMenu();
        
        startItem = new MenuItem("开启");
        stopItem = new MenuItem("停止");
        settingsItem = new MenuItem("设置...");
        exitItem = new MenuItem("退出");
        
        // 添加菜单项事件
        startItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                logger.info("用户点击: 开启");
                if (callback != null) {
                    callback.onStart();
                }
            }
        });
        
        stopItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                logger.info("用户点击: 停止");
                if (callback != null) {
                    callback.onStop();
                }
            }
        });
        
        settingsItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                logger.info("用户点击: 设置");
                if (callback != null) {
                    callback.onSettings();
                }
            }
        });
        
        exitItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                logger.info("用户点击: 退出");
                if (callback != null) {
                    callback.onExit();
                }
            }
        });
        
        popupMenu.add(startItem);
        popupMenu.add(stopItem);
        popupMenu.addSeparator();
        popupMenu.add(settingsItem);
        popupMenu.addSeparator();
        popupMenu.add(exitItem);
        
        // 初始状态：启用"开启"，禁用"停止"
        startItem.setEnabled(true);
        stopItem.setEnabled(false);
    }
    
    /**
     * 更新菜单状态（运行/停止）
     * 
     * @param isRunning 是否正在运行
     */
    public void updateMenuState(boolean isRunning) {
        startItem.setEnabled(!isRunning);
        stopItem.setEnabled(isRunning);
        
        // 更新图标
        String iconResource = isRunning ? "/screenshot_running.png" : "/screenshot_stopped.png";
        Image image = loadIcon(iconResource);
        trayIcon.setImage(image);
        
        // 更新提示文本
        String tooltip = isRunning ? "截图工具 - 运行中" : "截图工具 - 已停止";
        trayIcon.setToolTip(tooltip);
        
        logger.debug("托盘状态更新: {}", isRunning ? "运行中" : "已停止");
    }
    
    /**
     * 加载图标
     * 
     * @param resourcePath 资源路径
     * @return 图标图像
     */
    private Image loadIcon(String resourcePath) {
        try {
            InputStream is = getClass().getResourceAsStream(resourcePath);
            if (is != null) {
                Image image = Toolkit.getDefaultToolkit().createImage(readAllBytes(is));
                return image;
            }
        } catch (Exception e) {
            logger.warn("加载图标失败: {}", resourcePath);
        }
        
        // 返回默认图标(16x16灰色方块)
        return Toolkit.getDefaultToolkit().createImage(new byte[0]);
    }
    
    /**
     * 读取输入流的所有字节
     * 
     * @param is 输入流
     * @return 字节数组
     * @throws IOException 读取失败时抛出异常
     */
    private byte[] readAllBytes(InputStream is) throws IOException {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = is.read(buffer)) != -1) {
            baos.write(buffer, 0, len);
        }
        return baos.toByteArray();
    }
    
    /**
     * 移除托盘图标
     */
    public void remove() {
        if (systemTray != null && trayIcon != null) {
            systemTray.remove(trayIcon);
            logger.info("托盘图标已移除");
        }
    }
    
    /**
     * 显示消息提示
     * 
     * @param caption 标题
     * @param text 消息内容
     * @param type 消息类型
     */
    public void showMessage(String caption, String text, TrayIcon.MessageType type) {
        if (trayIcon != null) {
            trayIcon.displayMessage(caption, text, type);
        }
    }
}
