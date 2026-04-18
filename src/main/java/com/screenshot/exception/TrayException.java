package com.screenshot.exception;

/**
 * 托盘异常
 * 用于系统托盘初始化、操作过程中的错误
 * 
 * @author wsj
 * @version 1.0.0
 */
public class TrayException extends Exception {
    
    /**
     * 创建托盘异常
     * 
     * @param message 异常消息
     */
    public TrayException(String message) {
        super(message);
    }
    
    /**
     * 创建托盘异常（带原因）
     * 
     * @param message 异常消息
     * @param cause 异常原因
     */
    public TrayException(String message, Throwable cause) {
        super(message, cause);
    }
}
