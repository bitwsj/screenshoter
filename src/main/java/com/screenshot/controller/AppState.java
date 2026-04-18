package com.screenshot.controller;

import com.screenshot.config.AppConfig;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 应用程序共享状态
 * 线程安全的状态管理
 * 
 * @author wsj
 * @version 1.0.0
 */
public class AppState {
    /** 配置对象（使用volatile保证可见性） */
    private volatile AppConfig config;
    
    /** 运行状态标志（线程安全） */
    private AtomicBoolean running;
    
    /** 定时任务Future（用于取消） */
    private ScheduledFuture<?> scheduledFuture;
    
    /**
     * 创建应用状态
     * 
     * @param config 初始配置
     */
    public AppState(AppConfig config) {
        this.config = config;
        this.running = new AtomicBoolean(false);
    }
    
    /**
     * 获取配置
     * 
     * @return 当前配置
     */
    public AppConfig getConfig() {
        return config;
    }
    
    /**
     * 设置配置
     * 
     * @param config 新配置
     */
    public void setConfig(AppConfig config) {
        this.config = config;
    }
    
    /**
     * 检查是否正在运行
     * 
     * @return 是否正在运行
     */
    public boolean isRunning() {
        return running.get();
    }
    
    /**
     * 设置运行状态
     * 
     * @param running 运行状态
     */
    public void setRunning(boolean running) {
        this.running.set(running);
    }
    
    /**
     * 获取定时任务Future
     * 
     * @return ScheduledFuture
     */
    public ScheduledFuture<?> getScheduledFuture() {
        return scheduledFuture;
    }
    
    /**
     * 设置定时任务Future
     * 
     * @param scheduledFuture ScheduledFuture
     */
    public void setScheduledFuture(ScheduledFuture<?> scheduledFuture) {
        this.scheduledFuture = scheduledFuture;
    }
}
