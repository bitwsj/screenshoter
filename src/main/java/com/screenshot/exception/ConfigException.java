package com.screenshot.exception;

/**
 * 配置异常
 * 用于配置加载、保存、验证过程中的错误
 * 
 * @author wsj
 * @version 1.0.0
 */
public class ConfigException extends Exception {
    
    /**
     * 创建配置异常
     * 
     * @param message 异常消息
     */
    public ConfigException(String message) {
        super(message);
    }
    
    /**
     * 创建配置异常（带原因）
     * 
     * @param message 异常消息
     * @param cause 异常原因
     */
    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
