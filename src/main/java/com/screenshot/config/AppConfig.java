package com.screenshot.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.screenshot.exception.ConfigException;
import com.screenshot.scheduler.UserActivityMonitor;

import java.io.File;

/**
 * 应用程序配置
 *
 * @author wsj
 * @version 2.0.0
 */
public class AppConfig {
    /** 旧版截图间隔（秒），仅用于向后兼容迁移，不持久化到新格式 */
    @JsonIgnore
    private transient boolean migrated = false;

    /** 旧版字段，用于兼容读取旧配置文件 */
    @JsonProperty("interval")
    private Long intervalLegacy;

    /** 电脑空闲时段截图间隔（秒），范围1-3600，默认300（5分钟） */
    @JsonProperty("idle_interval")
    private long idleInterval = 300;

    /** 电脑忙碌时段截图间隔（秒），范围1-3600，默认60（1分钟） */
    @JsonProperty("busy_interval")
    private long busyInterval = 60;

    /** 空闲判定阈值（秒），无键鼠操作超过此时间判定为空闲，默认120秒（2分钟） */
    @JsonProperty("idle_threshold")
    private long idleThreshold = UserActivityMonitor.DEFAULT_IDLE_THRESHOLD_SECONDS;

    /** 截图保存路径，默认为程序目录下的screenshots文件夹 */
    @JsonProperty("save_path")
    private String savePath = "./screenshots";

    /** 图片格式，默认PNG */
    @JsonProperty("image_format")
    private ImageFormat imageFormat = ImageFormat.PNG;

    /** 是否启用每日自动归档，默认启用 */
    @JsonProperty("archive_enabled")
    private boolean archiveEnabled = true;

    /** 每日归档执行时间（HH:mm格式），默认凌晨00:30 */
    @JsonProperty("archive_time")
    private String archiveTime = "00:30";

    /** 是否正在运行（运行时状态，不持久化） */
    private transient boolean running = false;

    /**
     * 默认构造函数
     */
    public AppConfig() {
    }

    /**
     * 从旧版interval字段迁移到idleInterval/busyInterval
     * 如果检测到旧版interval字段且idleInterval/busyInterval未设置，则进行迁移
     */
    public void migrateIfNeeded() {
        if (!migrated && intervalLegacy != null && intervalLegacy > 0) {
            // 旧interval作为空闲间隔（用户通常是设置较长间隔），
            // 忙碌间隔取旧值的1/5，但不低于10秒
            idleInterval = intervalLegacy;
            busyInterval = Math.max(10, intervalLegacy / 5);
            intervalLegacy = null;
            migrated = true;
        }
    }

    /**
     * 检查是否已从旧版配置迁移
     *
     * @return 是否发生了迁移
     */
    public boolean isMigrated() {
        return migrated;
    }

    /**
     * 获取空闲时段截图间隔时间
     *
     * @return 间隔时间（秒）
     */
    public long getIdleInterval() {
        return idleInterval;
    }

    /**
     * 设置空闲时段截图间隔时间
     *
     * @param idleInterval 间隔时间（秒）
     */
    public void setIdleInterval(long idleInterval) {
        this.idleInterval = idleInterval;
    }

    /**
     * 获取忙碌时段截图间隔时间
     *
     * @return 间隔时间（秒）
     */
    public long getBusyInterval() {
        return busyInterval;
    }

    /**
     * 设置忙碌时段截图间隔时间
     *
     * @param busyInterval 间隔时间（秒）
     */
    public void setBusyInterval(long busyInterval) {
        this.busyInterval = busyInterval;
    }

    /**
     * 获取空闲判定阈值（秒）
     *
     * @return 阈值秒数
     */
    public long getIdleThreshold() {
        return idleThreshold;
    }

    /**
     * 设置空闲判定阈值（秒）
     *
     * @param idleThreshold 阈值秒数
     */
    public void setIdleThreshold(long idleThreshold) {
        this.idleThreshold = idleThreshold;
    }

    /**
     * 获取保存路径
     *
     * @return 保存路径
     */
    public String getSavePath() {
        return savePath;
    }

    /**
     * 设置保存路径
     *
     * @param savePath 保存路径
     */
    public void setSavePath(String savePath) {
        this.savePath = savePath;
    }

    /**
     * 获取图片格式
     *
     * @return 图片格式
     */
    public ImageFormat getImageFormat() {
        return imageFormat;
    }

    /**
     * 设置图片格式
     *
     * @param imageFormat 图片格式
     */
    public void setImageFormat(ImageFormat imageFormat) {
        this.imageFormat = imageFormat;
    }

    /**
     * 是否启用每日自动归档
     *
     * @return 是否启用
     */
    public boolean isArchiveEnabled() {
        return archiveEnabled;
    }

    /**
     * 设置是否启用每日自动归档
     *
     * @param archiveEnabled 是否启用
     */
    public void setArchiveEnabled(boolean archiveEnabled) {
        this.archiveEnabled = archiveEnabled;
    }

    /**
     * 获取每日归档执行时间（HH:mm格式）
     *
     * @return 时间字符串，如 "00:30"
     */
    public String getArchiveTime() {
        return archiveTime;
    }

    /**
     * 设置每日归档执行时间
     *
     * @param archiveTime 时间字符串（HH:mm格式）
     */
    public void setArchiveTime(String archiveTime) {
        this.archiveTime = archiveTime;
    }

    /**
     * 获取运行状态
     *
     * @return 是否正在运行
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * 设置运行状态
     *
     * @param running 运行状态
     */
    public void setRunning(boolean running) {
        this.running = running;
    }

    /**
     * 验证配置有效性
     *
     * @throws ConfigException 配置无效时抛出异常
     */
    public void validate() throws ConfigException {
        // 迁移旧配置
        migrateIfNeeded();

        // 验证空闲间隔范围
        if (idleInterval < 1 || idleInterval > 3600) {
            throw new ConfigException("无效的空闲时段间隔时间: " + idleInterval + "，有效范围: 1-3600秒");
        }

        // 验证忙碌间隔范围
        if (busyInterval < 1 || busyInterval > 3600) {
            throw new ConfigException("无效的忙碌时段间隔时间: " + busyInterval + "，有效范围: 1-3600秒");
        }

        // 验证空闲阈值范围
        if (idleThreshold < 10 || idleThreshold > 3600) {
            throw new ConfigException("无效的空闲判定阈值: " + idleThreshold + "，有效范围: 10-3600秒");
        }

        // 验证保存路径
        File path = new File(savePath);
        if (!path.exists()) {
            // 尝试创建路径
            if (!path.mkdirs()) {
                throw new ConfigException("无法创建保存路径: " + savePath);
            }
        }

        // 验证图片格式
        if (imageFormat == null) {
            throw new ConfigException("图片格式不能为空");
        }

        // 验证归档时间格式
        if (archiveTime != null && !archiveTime.matches("^([01]\\d|2[0-3]):([0-5]\\d)$")) {
            throw new ConfigException("无效的归档时间格式: " + archiveTime + "，应为HH:mm格式，如 00:30");
        }
    }

    @Override
    public String toString() {
        return "AppConfig{" +
                "idleInterval=" + idleInterval +
                ", busyInterval=" + busyInterval +
                ", idleThreshold=" + idleThreshold +
                ", savePath='" + savePath + '\'' +
                ", imageFormat=" + imageFormat +
                ", archiveEnabled=" + archiveEnabled +
                ", archiveTime='" + archiveTime + '\'' +
                ", running=" + running +
                '}';
    }
}
