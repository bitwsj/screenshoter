package com.screenshot.exception;

/**
 * 截图异常
 * 用于截图捕获、保存过程中的错误
 * 
 * @author wsj
 * @version 1.0.0
 */
public class ScreenshotException extends Exception {
    
    /**
     * 创建截图异常
     * 
     * @param message 异常消息
     */
    public ScreenshotException(String message) {
        super(message);
    }
    
    /**
     * 创建截图异常（带原因）
     * 
     * @param message 异常消息
     * @param cause 异常原因
     */
    public ScreenshotException(String message, Throwable cause) {
        super(message, cause);
    }
}
