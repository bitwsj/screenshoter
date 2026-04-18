package com.screenshot;

import com.screenshot.controller.Controller;
import com.screenshot.tray.TrayManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;

/**
 * Windows截图程序主入口
 * 
 * @author wsj
 * @version 1.0.0
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    
    public static void main(String[] args) {
        logger.info("Windows截图程序启动中...");
        System.out.println("Windows截图程序启动中...");
        
        try {
            // 创建控制器
            Controller controller = new Controller();
            
            // 创建托盘管理器
            TrayManager trayManager = new TrayManager(controller);
            controller.setTrayManager(trayManager);
            
            logger.info("程序初始化完成");
            System.out.println("程序初始化完成，托盘图标已显示");
            System.out.println("右键点击托盘图标可以开始截图");
            
            // 保持程序运行
            CountDownLatch latch = new CountDownLatch(1);
            
            // 添加关闭钩子
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override
                public void run() {
                    logger.info("程序正在关闭...");
                    latch.countDown();
                }
            }));
            
            // 等待程序关闭
            latch.await();
            
        } catch (Exception e) {
            logger.error("程序启动失败", e);
            System.err.println("程序启动失败: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
