package com.screenshot.config;

/**
 * 支持的图片格式枚举
 * 
 * @author wsj
 * @version 1.0.0
 */
public enum ImageFormat {
    /** PNG格式（无损压缩） */
    PNG("png"),
    
    /** JPG格式（有损压缩） */
    JPG("jpg"),
    
    /** BMP格式（无压缩） */
    BMP("bmp");
    
    private String extension;
    
    /**
     * 构造图片格式
     * 
     * @param extension 文件扩展名
     */
    ImageFormat(String extension) {
        this.extension = extension;
    }
    
    /**
     * 获取文件扩展名
     * 
     * @return 文件扩展名
     */
    public String getExtension() {
        return extension;
    }
}
