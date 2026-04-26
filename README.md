# Windows Screenshoter

一个轻量级的 Windows 定时截图工具，通过系统托盘静默运行。支持根据键鼠活动自动切换空闲/忙碌状态并调整截图频率，全局快捷键手动截图，每日自动按日期归档截图文件。

## 功能特性

- **智能截图频率** — 通过 Windows API 检测键鼠活动，自动切换空闲/忙碌状态并调整截图间隔
  - 检测到键鼠操作 → 立即切换忙碌，按忙碌间隔截图
  - 无操作超过阈值 → 切换空闲，按空闲间隔截图
  - 状态切换时立即截图，无延迟响应
- **全局快捷键截图** — 通过 Windows RegisterHotKey API 注册系统级热键，任何状态下按快捷键立即截图，快捷键可在设置中自定义（支持 Ctrl/Alt/Shift/Win + A-Z/0-9/F1-F12）
- **每日自动归档** — 定时将前一天的截图移入 `YYYY-MM-DD` 日期子目录
- **系统托盘运行** — 最小化到托盘，右键菜单控制启停和设置
- **图形化设置界面** — 所有参数均可通过设置对话框配置
- **配置持久化** — JSON 格式配置文件，修改即时生效无需重启
- **多格式支持** — PNG、JPG、BMP

## 归档运行效果

```
截图保存目录/
├── screenshot_20260418_091512.png        ← 今天的截图
├── screenshot_20260418_143025.png
├── 2026-04-17/                           ← 归档目录
│   ├── screenshot_20260417_083012.png
│   └── screenshot_20260417_175930.png
└── 2026-04-16/
    └── screenshot_20260416_123045.png
```

## 技术栈

| 组件 | 技术 |
|------|------|
| 语言 | Java 8 |
| 构建 | Maven |
| 截图 | java.awt.Robot |
| 系统托盘 | java.awt.SystemTray |
| 空闲检测 | JNA → Windows GetLastInputInfo API |
| 全局热键 | JNA → Windows RegisterHotKey API |
| 配置管理 | Jackson (JSON) |
| 调度 | ScheduledExecutorService（事件驱动 + 自调度） |
| 日志 | SLF4J + Logback |

## 项目结构

```
src/main/java/com/screenshot/
├── Main.java                          # 程序入口
├── config/
│   ├── AppConfig.java                 # 配置模型（含双间隔 + 归档配置）
│   ├── ConfigManager.java             # 配置加载/保存/迁移
│   └── ImageFormat.java               # 图片格式枚举
├── controller/
│   ├── Controller.java                # 核心控制器（协调截图 + 归档任务）
│   └── AppState.java                  # 应用运行时状态
├── engine/
│   ├── ScreenshotEngine.java          # 截图引擎（Robot 屏幕捕获）
│   └── ScreenshotResult.java          # 截图结果
├── scheduler/
│   ├── Scheduler.java                 # 调度器（事件驱动，状态切换回调）
│   ├── UserActivityMonitor.java       # 用户活动监控（JNA + 后台线程）
│   └── Archiver.java                  # 每日归档器
├── hotkey/
│   └── GlobalHotkeyManager.java       # 全局热键管理（JNA + Windows 消息循环）
├── tray/
│   └── TrayManager.java               # 系统托盘管理
├── ui/
│   └── SettingsDialog.java            # 设置对话框
└── exception/                         # 异常体系
```

## 构建与运行

### 前置要求

- JDK 8 或更高版本
- Maven 3.6+

### 构建

```bash
mvn clean package
```

### 运行

```bash
java -jar target/screenshoter-1.0.0.jar
```

> Windows 控制台建议先执行 `chcp 65001` 切换到 UTF-8 编码以正常显示中文日志。

## 配置说明

程序运行后自动在当前目录生成 `config.json`：

```json
{
  "idle_interval": 300,
  "busy_interval": 60,
  "idle_threshold": 120,
  "hotkey_enabled": true,
  "hotkey": "Ctrl+Alt+S",
  "archive_enabled": true,
  "archive_time": "00:30",
  "save_path": "C:\\Users\\xxx\\AutoScreenshot",
  "image_format": "PNG"
}
```

| 字段 | 说明 | 范围 | 默认值 |
|------|------|------|--------|
| `idle_interval` | 空闲时段截图间隔（秒） | 1-3600 | 300（5分钟） |
| `busy_interval` | 忙碌时段截图间隔（秒） | 1-3600 | 60（1分钟） |
| `idle_threshold` | 空闲判定阈值（秒），无键鼠操作超过此时间判定为空闲 | 10-3600 | 120（2分钟） |
| `hotkey_enabled` | 是否启用全局快捷键手动截图 | true/false | true |
| `hotkey` | 快捷键组合（修饰键+按键） | Ctrl/Alt/Shift/Win + A-Z/0-9/F1-F12 | "Ctrl+Alt+S" |
| `archive_enabled` | 是否启用每日自动归档 | true/false | true |
| `archive_time` | 归档执行时间（HH:mm） | 00:00-23:59 | "00:30" |
| `save_path` | 截图保存路径 | 有效路径 | ./screenshots |
| `image_format` | 图片格式 | PNG/JPG/BMP | PNG |

### 截图文件命名

```
screenshot_YYYYMMDD_HHmmss.{format}
```

示例：`screenshot_20260418_143025.png`

### 旧版配置自动迁移

如果配置文件中存在旧版 `interval` 字段，程序会自动迁移：
- `interval` → `idle_interval`
- `busy_interval` = max(10, interval / 5)

## 使用说明

1. 启动后程序最小化到系统托盘
2. 右键托盘图标：
   - **开启** — 启动定时截图
   - **停止** — 停止截图
   - **设置** — 打开配置界面
   - **退出** — 关闭程序

## 许可证

MIT License

## 作者

wsj

## 版本历史

- **v2.1.0** (2026-04-20)
  - 实现全局快捷键手动截图（Windows RegisterHotKey API）
  - 设置界面支持快捷键录制和自定义
  - 修复调度器竞态条件导致空闲状态持续忙碌间隔截图的问题
- **v2.0.0** (2026-04-18)
  - 实现空闲/忙碌双时段智能截图频率
  - 实现事件驱动调度模型（JNA 检测键鼠活动）
  - 实现每日自动归档功能
  - 统一日志 UTF-8 编码
- **v1.0.0** (2025-04-02)
  - 初始版本：基础定时截图 + 系统托盘 + 配置管理
