package com.screenshot.hotkey;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.win32.StdCallLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 全局热键管理器
 * 通过 Windows API RegisterHotKey 注册系统级全局热键，
 * 后台 daemon 线程维护 Windows 消息循环接收 WM_HOTKEY 消息。
 *
 * 热键字符串格式：修饰键+按键，如 "Ctrl+Alt+S"
 * 支持的修饰键：Ctrl, Alt, Shift, Win
 * 支持的按键：A-Z, 0-9, F1-F12
 *
 * 线程安全：
 * - register/unregister 方法通过 synchronized 保证互斥
 * - 注册结果通过 CountDownLatch 同步返回给调用方
 * - 回调在热键消息循环线程上执行，调用方需注意线程安全
 *
 * @author wsj
 * @version 1.0.0
 */
public class GlobalHotkeyManager {
    private static final Logger logger = LoggerFactory.getLogger(GlobalHotkeyManager.class);

    // Windows 消息常量
    private static final int WM_HOTKEY = 0x0312;
    private static final int WM_QUIT = 0x0012;

    /** 热键 ID（应用内唯一标识符） */
    private static final int HOTKEY_ID = 1;

    // Windows 修饰键标志
    private static final int MOD_ALT = 0x0001;
    private static final int MOD_CONTROL = 0x0002;
    private static final int MOD_SHIFT = 0x0004;
    private static final int MOD_WIN = 0x0008;

    // ========== JNA 接口定义 ==========

    /**
     * user32.dll 扩展接口（热键注册 + 消息循环）
     */
    public interface User32Ex extends StdCallLibrary {
        User32Ex INSTANCE = Native.load("user32", User32Ex.class);

        boolean RegisterHotKey(Pointer hWnd, int id, int fsModifiers, int vk);
        boolean UnregisterHotKey(Pointer hWnd, int id);
        int GetMessageW(MSG lpMsg, Pointer hWnd, int wMsgFilterMin, int wMsgFilterMax);
        boolean PostThreadMessageW(int idThread, int Msg, Pointer wParam, Pointer lParam);
    }

    /**
     * kernel32.dll 扩展接口（获取线程 ID）
     */
    public interface Kernel32Ex extends StdCallLibrary {
        Kernel32Ex INSTANCE = Native.load("kernel32", Kernel32Ex.class);
        int GetCurrentThreadId();
    }

    // ========== JNA 结构体 ==========

    /**
     * Windows POINT 结构体
     */
    public static class POINT extends Structure {
        public int x;
        public int y;

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("x", "y");
        }
    }

    /**
     * Windows MSG 结构体
     * 字段布局由 JNA 自动对齐，兼容 32/64 位系统
     */
    public static class MSG extends Structure {
        public Pointer hWnd;
        public int message;
        public Pointer wParam;
        public Pointer lParam;
        public int time;
        public POINT pt;

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("hWnd", "message", "wParam", "lParam", "time", "pt");
        }
    }

    // ========== 回调接口 ==========

    /**
     * 热键触发回调
     */
    public interface HotkeyCallback {
        /**
         * 热键被按下时调用（在热键消息循环线程上执行）
         */
        void onHotkeyPressed();
    }

    // ========== 实例字段 ==========

    private HotkeyCallback callback;
    private Thread hotkeyThread;
    private volatile boolean registered = false;
    private volatile boolean running = false;
    private String currentHotkey;

    /** 热键线程的 Windows 线程 ID，用于发送 WM_QUIT 停止消息循环 */
    private final AtomicInteger hotkeyThreadId = new AtomicInteger(0);

    /** 注册结果同步：主线程等待热键线程完成 RegisterHotKey 调用 */
    private CountDownLatch registerLatch;
    private volatile boolean registerSuccess = false;

    /**
     * 设置热键触发回调
     *
     * @param callback 回调（在热键消息循环线程上执行，注意线程安全）
     */
    public void setCallback(HotkeyCallback callback) {
        this.callback = callback;
    }

    /**
     * 注册全局热键并启动监听
     *
     * @param hotkeyStr 热键字符串，如 "Ctrl+Alt+S"
     * @return true=注册成功，false=注册失败（可能被其他程序占用或格式错误）
     */
    public synchronized boolean register(String hotkeyStr) {
        unregister();

        int[] parsed = parseHotkey(hotkeyStr);
        if (parsed == null) {
            logger.error("无法解析快捷键: {}", hotkeyStr);
            return false;
        }

        int modifiers = parsed[0];
        int vk = parsed[1];

        registerLatch = new CountDownLatch(1);
        registerSuccess = false;

        startMessageLoop(modifiers, vk, hotkeyStr);

        // 等待热键线程完成注册（最多 5 秒）
        try {
            registerLatch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }

        return registerSuccess;
    }

    /**
     * 注销当前热键并停止消息循环
     */
    public synchronized void unregister() {
        if (!running && !registered) {
            return;
        }

        stopMessageLoop();
        registered = false;
        currentHotkey = null;
    }

    /**
     * 热键是否已注册
     *
     * @return true=已注册
     */
    public boolean isRegistered() {
        return registered;
    }

    /**
     * 获取当前注册的热键字符串
     *
     * @return 热键字符串，未注册时返回 null
     */
    public String getCurrentHotkey() {
        return currentHotkey;
    }

    /**
     * 关闭管理器，释放所有资源
     */
    public synchronized void shutdown() {
        unregister();
        logger.info("全局热键管理器已关闭");
    }

    // ========== 内部方法 ==========

    /**
     * 启动热键消息循环线程
     * RegisterHotKey 必须与 GetMessage 在同一线程（消息队列归属线程）
     */
    private void startMessageLoop(int modifiers, int vk, String hotkeyStr) {
        running = true;

        hotkeyThread = new Thread(new Runnable() {
            @Override
            public void run() {
                // 捕获 Windows 线程 ID（后续需要通过此 ID 发送 WM_QUIT）
                int tid = Kernel32Ex.INSTANCE.GetCurrentThreadId();
                hotkeyThreadId.set(tid);

                // 在此线程注册热键（WM_HOTKEY 消息发送到此线程的消息队列）
                boolean success = User32Ex.INSTANCE.RegisterHotKey(null, HOTKEY_ID, modifiers, vk);

                if (!success) {
                    logger.warn("注册全局热键失败: {}，可能已被其他程序占用", hotkeyStr);
                    registerSuccess = false;
                    running = false;
                    registerLatch.countDown();
                    return;
                }

                registered = true;
                currentHotkey = hotkeyStr;
                registerSuccess = true;
                registerLatch.countDown();

                logger.info("全局热键注册成功: {}", hotkeyStr);

                // Windows 消息循环（阻塞直到收到 WM_QUIT）
                MSG msg = new MSG();
                while (running) {
                    int ret = User32Ex.INSTANCE.GetMessageW(msg, null, 0, 0);
                    if (ret == 0 || ret == -1) {
                        // WM_QUIT (0) 或 错误 (-1)
                        break;
                    }

                    if (msg.message == WM_HOTKEY) {
                        logger.debug("热键触发: {}", currentHotkey);
                        HotkeyCallback cb = callback;
                        if (cb != null) {
                            try {
                                cb.onHotkeyPressed();
                            } catch (Exception e) {
                                logger.error("热键回调执行异常", e);
                            }
                        }
                    }
                }

                // 线程退出时注销热键
                User32Ex.INSTANCE.UnregisterHotKey(null, HOTKEY_ID);
                registered = false;
                logger.info("全局热键已注销: {}", hotkeyStr);
            }
        }, "GlobalHotkey");

        hotkeyThread.setDaemon(true);
        hotkeyThread.start();
    }

    /**
     * 停止消息循环线程
     * 通过向热键线程发送 WM_QUIT 消息唤醒 GetMessageW
     */
    private void stopMessageLoop() {
        running = false;

        int tid = hotkeyThreadId.get();
        if (tid != 0 && hotkeyThread != null && hotkeyThread.isAlive()) {
            // 向消息循环线程发送 WM_QUIT 以唤醒阻塞的 GetMessageW
            User32Ex.INSTANCE.PostThreadMessageW(tid, WM_QUIT, null, null);

            try {
                hotkeyThread.join(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        hotkeyThreadId.set(0);
        hotkeyThread = null;
    }

    // ========== 静态解析方法 ==========

    /**
     * 解析热键字符串为 Windows 修饰键标志 + 虚拟键码
     *
     * @param hotkeyStr 热键字符串，如 "Ctrl+Alt+S"、"Shift+F1"
     * @return int[2] {modifiers, vk}，解析失败返回 null
     */
    public static int[] parseHotkey(String hotkeyStr) {
        if (hotkeyStr == null || hotkeyStr.trim().isEmpty()) {
            return null;
        }

        String[] parts = hotkeyStr.trim().split("\\+");
        if (parts.length < 2) {
            return null;
        }

        // 最后一部分为按键，其余为修饰键
        int modifiers = 0;
        for (int i = 0; i < parts.length - 1; i++) {
            String mod = parts[i].trim().toLowerCase();
            switch (mod) {
                case "ctrl":
                case "control":
                    modifiers |= MOD_CONTROL;
                    break;
                case "alt":
                    modifiers |= MOD_ALT;
                    break;
                case "shift":
                    modifiers |= MOD_SHIFT;
                    break;
                case "win":
                case "windows":
                    modifiers |= MOD_WIN;
                    break;
                default:
                    return null;
            }
        }

        if (modifiers == 0) {
            return null;
        }

        String key = parts[parts.length - 1].trim();
        int vk = keyToVirtualKey(key);
        if (vk <= 0) {
            return null;
        }

        return new int[]{modifiers, vk};
    }

    /**
     * 将按键名称映射为 Windows 虚拟键码
     * 支持：A-Z, 0-9, F1-F12
     *
     * @param key 按键名称
     * @return 虚拟键码，无法识别返回 -1
     */
    private static int keyToVirtualKey(String key) {
        if (key.length() == 1) {
            char c = Character.toUpperCase(key.charAt(0));
            if (c >= 'A' && c <= 'Z') {
                return 0x41 + (c - 'A');
            }
            if (c >= '0' && c <= '9') {
                return 0x30 + (c - '0');
            }
        }

        // 功能键 F1-F12
        String upper = key.toUpperCase();
        if (upper.startsWith("F")) {
            try {
                int num = Integer.parseInt(upper.substring(1));
                if (num >= 1 && num <= 12) {
                    return 0x70 + num - 1;
                }
            } catch (NumberFormatException ignored) {
            }
        }

        return -1;
    }
}
