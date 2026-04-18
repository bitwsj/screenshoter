package com.screenshot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.screenshot.exception.ConfigException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;

/**
 * 配置管理器
 * 负责配置的加载、保存、更新
 * 
 * @author wsj
 * @version 1.0.0
 */
public class ConfigManager {
    private static final Logger logger = LoggerFactory.getLogger(ConfigManager.class);
    
    private Path configPath;
    private AppConfig config;
    private ObjectMapper objectMapper;
    
    /**
     * 创建配置管理器
     * 
     * @param configPath 配置文件路径
     * @throws ConfigException 配置加载失败时抛出异常
     */
    public ConfigManager(Path configPath) throws ConfigException {
        this.configPath = configPath;
        this.objectMapper = new ObjectMapper();
        load();
    }
    
    /**
     * 加载配置文件
     * 
     * @throws ConfigException 配置加载失败时抛出异常
     */
    public void load() throws ConfigException {
        File file = configPath.toFile();

        if (file.exists()) {
            try {
                logger.info("加载配置文件: {}", file.getAbsolutePath());
                config = objectMapper.readValue(file, AppConfig.class);

                if (config == null) {
                    logger.warn("配置文件为空，使用默认配置");
                    config = new AppConfig();
                }

                // 验证配置（内部会执行旧格式迁移）
                config.validate();

                // 如果发生了旧格式迁移，自动保存为新格式
                if (config.isMigrated()) {
                    logger.info("检测到旧版配置格式，自动迁移并保存");
                    save();
                }

                logger.info("配置加载成功: {}", config);

            } catch (ConfigException e) {
                // 配置验证失败，抛出异常
                throw e;
            } catch (Exception e) {
                // 解析失败，使用默认配置
                logger.warn("配置文件解析失败，使用默认配置: {}", e.getMessage());
                config = new AppConfig();
                try {
                    config.validate();
                } catch (ConfigException ex) {
                    // 默认配置验证失败，不应该发生
                    throw new ConfigException("默认配置验证失败", ex);
                }
            }
        } else {
            // 配置文件不存在，创建默认配置
            logger.info("配置文件不存在，创建默认配置");
            config = new AppConfig();
            try {
                config.validate();
                save();
            } catch (ConfigException e) {
                throw new ConfigException("默认配置创建失败", e);
            }
        }
    }
    
    /**
     * 保存配置文件
     * 
     * @throws ConfigException 配置保存失败时抛出异常
     */
    public void save() throws ConfigException {
        try {
            logger.info("保存配置文件: {}", configPath.toAbsolutePath());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(configPath.toFile(), config);
            logger.info("配置保存成功");
        } catch (Exception e) {
            logger.error("配置保存失败", e);
            throw new ConfigException("配置保存失败", e);
        }
    }
    
    /**
     * 获取当前配置
     * 
     * @return 当前配置
     */
    public AppConfig getConfig() {
        return config;
    }
    
    /**
     * 更新配置
     * 
     * @param newConfig 新配置
     * @throws ConfigException 配置更新失败时抛出异常
     */
    public void updateConfig(AppConfig newConfig) throws ConfigException {
        logger.info("更新配置: {}", newConfig);
        newConfig.validate();
        this.config = newConfig;
        save();
    }
    
    /**
     * 重置为默认配置
     * 
     * @throws ConfigException 重置失败时抛出异常
     */
    public void resetToDefault() throws ConfigException {
        logger.info("重置为默认配置");
        this.config = new AppConfig();
        save();
    }
}
