package com.screenshot.exception;

/**
 * 控制器异常
 * 用于核心控制器初始化、操作过程中的错误
 * 
 * @author wsj
 * @version 1.0.0
 */
public class ControllerException extends Exception {
    
    /**
     * 创建控制器异常
     * 
     * @param message 异常消息
     */
    public ControllerException(String message) {
        super(message);
    }
    
    /**
     * 创建控制器异常（带原因）
     * 
     * @param message 异常消息
     * @param cause 异常原因
     */
    public ControllerException(String message, Throwable cause) {
        super(message, cause);
    }
}
