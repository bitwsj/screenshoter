package com.screenshot.config;

import com.screenshot.exception.ConfigException;
import com.screenshot.scheduler.UserActivityMonitor;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * AppConfig测试类
 *
 * @author wsj
 * @version 2.0.0
 */
public class AppConfigTest {

    @Test
    public void testDefaultConfig() {
        AppConfig config = new AppConfig();
        assertEquals(300, config.getIdleInterval());
        assertEquals(60, config.getBusyInterval());
        assertEquals(UserActivityMonitor.DEFAULT_IDLE_THRESHOLD_SECONDS, config.getIdleThreshold());
        assertEquals("./screenshots", config.getSavePath());
        assertEquals(ImageFormat.PNG, config.getImageFormat());
        assertFalse(config.isRunning());
    }

    @Test
    public void testSettersAndGetters() {
        AppConfig config = new AppConfig();

        config.setIdleInterval(600);
        assertEquals(600, config.getIdleInterval());

        config.setBusyInterval(30);
        assertEquals(30, config.getBusyInterval());

        config.setIdleThreshold(180);
        assertEquals(180, config.getIdleThreshold());

        config.setSavePath("/tmp/screenshots");
        assertEquals("/tmp/screenshots", config.getSavePath());

        config.setImageFormat(ImageFormat.JPG);
        assertEquals(ImageFormat.JPG, config.getImageFormat());

        config.setRunning(true);
        assertTrue(config.isRunning());
    }

    @Test(expected = ConfigException.class)
    public void testInvalidIdleIntervalTooSmall() throws ConfigException {
        AppConfig config = new AppConfig();
        config.setIdleInterval(0);
        config.validate();
    }

    @Test(expected = ConfigException.class)
    public void testInvalidIdleIntervalTooLarge() throws ConfigException {
        AppConfig config = new AppConfig();
        config.setIdleInterval(3601);
        config.validate();
    }

    @Test(expected = ConfigException.class)
    public void testInvalidBusyIntervalTooSmall() throws ConfigException {
        AppConfig config = new AppConfig();
        config.setBusyInterval(0);
        config.validate();
    }

    @Test(expected = ConfigException.class)
    public void testInvalidBusyIntervalTooLarge() throws ConfigException {
        AppConfig config = new AppConfig();
        config.setBusyInterval(3601);
        config.validate();
    }

    @Test(expected = ConfigException.class)
    public void testInvalidIdleThresholdTooSmall() throws ConfigException {
        AppConfig config = new AppConfig();
        config.setIdleThreshold(5);
        config.validate();
    }

    @Test(expected = ConfigException.class)
    public void testInvalidIdleThresholdTooLarge() throws ConfigException {
        AppConfig config = new AppConfig();
        config.setIdleThreshold(3601);
        config.validate();
    }

    @Test
    public void testValidConfig() throws ConfigException {
        AppConfig config = new AppConfig();
        config.setIdleInterval(100);
        config.setBusyInterval(50);
        config.setIdleThreshold(120);
        config.validate();  // 不应抛出异常
    }

    @Test
    public void testImageFormatExtension() {
        assertEquals("png", ImageFormat.PNG.getExtension());
        assertEquals("jpg", ImageFormat.JPG.getExtension());
        assertEquals("bmp", ImageFormat.BMP.getExtension());
    }
}
