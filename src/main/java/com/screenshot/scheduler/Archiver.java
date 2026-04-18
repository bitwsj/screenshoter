package com.screenshot.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 截图归档器
 * 将截图目录中的文件按日期归档到以日期命名的子目录中
 *
 * 归档规则：
 * 1. 扫描截图保存目录下的所有文件
 * 2. 从文件名中提取日期（格式: screenshot_YYYYMMDD_HHmmss.ext）
 * 3. 将非今天的文件移动到 YYYY-MM-DD 子目录中
 *
 * 示例：
 *   截图目录/screenshot_20260417_143025.png
 *   → 截图目录/2026-04-17/screenshot_20260417_143025.png
 *
 * @author wsj
 * @version 1.0.0
 */
public class Archiver {
    private static final Logger logger = LoggerFactory.getLogger(Archiver.class);

    /** 文件名中的日期提取正则 */
    private static final Pattern DATE_PATTERN = Pattern.compile("screenshot_(\\d{8})_\\d{6}\\.\\w+");

    /** 子目录日期格式 */
    private static final DateTimeFormatter DIR_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 文件名中的日期格式 */
    private static final DateTimeFormatter FILENAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 执行归档操作
     * 将截图目录中非今天的文件按日期移动到对应子目录
     *
     * @param savePath 截图保存目录
     * @return 归档的文件数量
     */
    public int archive(Path savePath) {
        logger.info("开始执行截图归档，目录: {}", savePath);

        File dir = savePath.toFile();
        if (!dir.exists() || !dir.isDirectory()) {
            logger.warn("截图目录不存在或不是目录: {}", savePath);
            return 0;
        }

        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            logger.info("截图目录为空，无需归档");
            return 0;
        }

        LocalDate today = LocalDate.now();
        int archivedCount = 0;

        for (File file : files) {
            // 跳过子目录（包括已归档的日期目录）
            if (file.isDirectory()) {
                continue;
            }

            // 从文件名提取日期
            LocalDate fileDate = extractDateFromFilename(file.getName());
            if (fileDate == null) {
                logger.debug("跳过无法解析日期的文件: {}", file.getName());
                continue;
            }

            // 跳过今天的文件（不归档当天的截图）
            if (fileDate.equals(today)) {
                continue;
            }

            // 执行移动
            if (moveToDateDir(savePath, file, fileDate)) {
                archivedCount++;
            }
        }

        logger.info("归档完成，共归档 {} 个文件", archivedCount);
        return archivedCount;
    }

    /**
     * 从文件名中提取日期
     *
     * @param filename 文件名
     * @return 日期，如果无法解析则返回 null
     */
    private LocalDate extractDateFromFilename(String filename) {
        Matcher matcher = DATE_PATTERN.matcher(filename);
        if (matcher.matches()) {
            try {
                return LocalDate.parse(matcher.group(1), FILENAME_FORMATTER);
            } catch (DateTimeParseException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 将文件移动到日期子目录
     *
     * @param baseDir  基础截图目录
     * @param file     要移动的文件
     * @param fileDate 文件日期
     * @return 是否成功
     */
    private boolean moveToDateDir(Path baseDir, File file, LocalDate fileDate) {
        String dirName = fileDate.format(DIR_FORMATTER);
        Path dateDir = baseDir.resolve(dirName);

        // 创建日期子目录
        File dateDirFile = dateDir.toFile();
        if (!dateDirFile.exists()) {
            if (dateDirFile.mkdirs()) {
                logger.debug("创建归档目录: {}", dateDir);
            } else {
                logger.error("无法创建归档目录: {}", dateDir);
                return false;
            }
        }

        // 移动文件
        Path target = dateDir.resolve(file.getName());
        try {
            Files.move(file.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
            logger.debug("归档: {} → {}", file.getName(), dirName + "/");
            return true;
        } catch (IOException e) {
            logger.error("归档文件失败: {} → {}", file.getName(), target, e);
            return false;
        }
    }
}
