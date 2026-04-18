package com.screenshot.engine;

import java.time.LocalDateTime;

/**
 * 截图结果
 * 包含截图文件的详细信息
 * 
 * @author wsj
 * @version 1.0.0
 */
public class ScreenshotResult {
    /** 文件路径 */
    private String filePath;
    
    /** 文件大小（字节） */
    private long fileSize;
    
    /** 截图时间戳 */
    private LocalDateTime timestamp;
    
    /**
     * 创建截图结果
     * 
     * @param filePath 文件路径
     * @param fileSize 文件大小（字节）
     * @param timestamp 截图时间戳
     */
    public ScreenshotResult(String filePath, long fileSize, LocalDateTime timestamp) {
        this.filePath = filePath;
        this.fileSize = fileSize;
        this.timestamp = timestamp;
    }
    
    /**
     * 获取文件路径
     * 
     * @return 文件路径
     */
    public String getFilePath() {
        return filePath;
    }
    
    /**
     * 获取文件大小
     * 
     * @return 文件大小（字节）
     */
    public long getFileSize() {
        return fileSize;
    }
    
    /**
     * 获取截图时间戳
     * 
     * @return 截图时间戳
     */
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    @Override
    public String toString() {
        return "ScreenshotResult{" +
                "filePath='" + filePath + '\'' +
                ", fileSize=" + fileSize +
                ", timestamp=" + timestamp +
                '}';
    }
}
