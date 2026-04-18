package com.screenshot.engine;

import com.screenshot.config.ImageFormat;
import com.screenshot.exception.ScreenshotException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 截图引擎
 * 负责屏幕捕获和图片保存
 * 
 * @author wsj
 * @version 1.0.0
 */
public class ScreenshotEngine {
    private static final Logger logger = LoggerFactory.getLogger(ScreenshotEngine.class);
    
    private Path savePath;
    private ImageFormat imageFormat;
    private Robot robot;
    
    /**
     * 创建截图引擎
     * 
     * @param savePath 保存路径
     * @param imageFormat 图片格式
     * @throws ScreenshotException 创建失败时抛出异常
     */
    public ScreenshotEngine(Path savePath, ImageFormat imageFormat) throws ScreenshotException {
        this.savePath = savePath;
        this.imageFormat = imageFormat;
        
        try {
            this.robot = new Robot();
            logger.info("截图引擎初始化成功");
        } catch (AWTException e) {
            logger.error("无法创建Robot实例", e);
            throw new ScreenshotException("无法创建Robot实例", e);
        }
    }
    
    /**
     * 执行截图
     * 
     * @return 截图结果
     * @throws ScreenshotException 截图失败时抛出异常
     */
    public ScreenshotResult capture() throws ScreenshotException {
        try {
            logger.debug("开始执行截图");
            
            // 获取屏幕尺寸
            Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            logger.debug("屏幕尺寸: {}x{}", screenRect.width, screenRect.height);
            
            // 捕获屏幕
            BufferedImage image = robot.createScreenCapture(screenRect);
            logger.debug("屏幕捕获完成");
            
            // 生成文件名
            String filename = generateFilename();
            File outputFile = savePath.resolve(filename).toFile();
            
            // 确保目录存在
            if (!outputFile.getParentFile().exists()) {
                if (!outputFile.getParentFile().mkdirs()) {
                    throw new ScreenshotException("无法创建保存目录: " + outputFile.getParent());
                }
                logger.debug("创建保存目录: {}", outputFile.getParent());
            }
            
            // 保存图片
            saveImage(image, outputFile);
            logger.info("截图保存成功: {} ({} 字节)", outputFile.getAbsolutePath(), outputFile.length());
            
            // 返回结果
            return new ScreenshotResult(
                outputFile.getAbsolutePath(),
                outputFile.length(),
                LocalDateTime.now()
            );
            
        } catch (ScreenshotException e) {
            throw e;
        } catch (Exception e) {
            logger.error("截图失败", e);
            throw new ScreenshotException("截图失败", e);
        }
    }
    
    /**
     * 更新保存路径
     * 
     * @param savePath 新的保存路径
     */
    public void setSavePath(Path savePath) {
        this.savePath = savePath;
        logger.info("更新保存路径: {}", savePath);
    }
    
    /**
     * 更新图片格式
     * 
     * @param imageFormat 新的图片格式
     */
    public void setImageFormat(ImageFormat imageFormat) {
        this.imageFormat = imageFormat;
        logger.info("更新图片格式: {}", imageFormat);
    }
    
    /**
     * 生成文件名
     * 格式: screenshot_YYYYMMDD_HHMMSS.{extension}
     * 
     * @return 文件名
     */
    private String generateFilename() {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        return "screenshot_" + now.format(formatter) + "." + imageFormat.getExtension();
    }
    
    /**
     * 保存图片
     * 使用缓冲输出流提高性能
     * 
     * @param image 图片
     * @param file 目标文件
     * @throws IOException 保存失败时抛出异常
     */
    private void saveImage(BufferedImage image, File file) throws IOException {
        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file), 8192)) {
            ImageIO.write(image, imageFormat.getExtension(), bos);
            bos.flush();
        }
    }
}
