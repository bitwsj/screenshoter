package com.screenshot.engine;

import com.screenshot.config.ImageFormat;
import com.screenshot.exception.ScreenshotException;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

/**
 * ScreenshotEngine测试类
 * 
 * @author wsj
 * @version 1.0.0
 */
public class ScreenshotEngineTest {
    
    @Test
    public void testScreenshotEngineCreation() throws ScreenshotException {
        // 创建临时目录
        Path tempDir;
        try {
            tempDir = Files.createTempDirectory("screenshot_test");
        } catch (Exception e) {
            fail("无法创建临时目录: " + e.getMessage());
            return;
        }
        
        // 创建截图引擎
        ScreenshotEngine engine = new ScreenshotEngine(tempDir, ImageFormat.PNG);
        assertNotNull(engine);
        
        // 清理
        try {
            Files.deleteIfExists(tempDir);
        } catch (Exception e) {
            // 忽略清理错误
        }
    }
    
    @Test
    public void testCapture() throws ScreenshotException {
        // 创建临时目录
        Path tempDir;
        try {
            tempDir = Files.createTempDirectory("screenshot_test");
        } catch (Exception e) {
            fail("无法创建临时目录: " + e.getMessage());
            return;
        }
        
        try {
            // 创建截图引擎
            ScreenshotEngine engine = new ScreenshotEngine(tempDir, ImageFormat.PNG);
            
            // 执行截图
            ScreenshotResult result = engine.capture();
            
            // 验证结果
            assertNotNull(result);
            assertNotNull(result.getFilePath());
            assertTrue(result.getFileSize() > 0);
            assertNotNull(result.getTimestamp());
            
            // 验证文件存在
            assertTrue(Files.exists(java.nio.file.Paths.get(result.getFilePath())));
            
        } finally {
            // 清理
            try {
                Files.walk(tempDir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (Exception e) {
                            // 忽略删除错误
                        }
                    });
            } catch (Exception e) {
                // 忽略清理错误
            }
        }
    }
    
    @Test
    public void testDifferentFormats() throws ScreenshotException {
        // 创建临时目录
        Path tempDir;
        try {
            tempDir = Files.createTempDirectory("screenshot_test");
        } catch (Exception e) {
            fail("无法创建临时目录: " + e.getMessage());
            return;
        }
        
        try {
            // 测试PNG格式
            ScreenshotEngine pngEngine = new ScreenshotEngine(tempDir, ImageFormat.PNG);
            ScreenshotResult pngResult = pngEngine.capture();
            assertTrue(pngResult.getFilePath().endsWith(".png"));
            
            // 测试JPG格式
            ScreenshotEngine jpgEngine = new ScreenshotEngine(tempDir, ImageFormat.JPG);
            ScreenshotResult jpgResult = jpgEngine.capture();
            assertTrue(jpgResult.getFilePath().endsWith(".jpg"));
            
            // 测试BMP格式
            ScreenshotEngine bmpEngine = new ScreenshotEngine(tempDir, ImageFormat.BMP);
            ScreenshotResult bmpResult = bmpEngine.capture();
            assertTrue(bmpResult.getFilePath().endsWith(".bmp"));
            
        } finally {
            // 清理
            try {
                Files.walk(tempDir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (Exception e) {
                            // 忽略删除错误
                        }
                    });
            } catch (Exception e) {
                // 忽略清理错误
            }
        }
    }
}
