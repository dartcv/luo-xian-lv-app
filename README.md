# 落弦律 Android 客户端

负责曲库、谱面导入、无障碍按比例点击和游戏内悬浮窗。

```powershell
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

## 项目结构

UI 层为 Jetpack Compose + Material 3（单 Activity + HorizontalPager），
业务逻辑为纯 Kotlin 分层包。重构目标与验收清单见
[docs/ui-compose-refactor.md](docs/ui-compose-refactor.md)。

```text
app/src/main/java/com/example/autoplaymusic/
├── MainActivity.kt        # Compose 入口（setContent + 服务/热更新对齐）
├── core/                  # 领域逻辑（无 Android UI 依赖）
│   ├── score/             #   MIDI/简谱解析、时序、编配
│   ├── harmonica/         #   口风琴编译器、音高映射、Rust 桥
│   └── playback/          #   播放时间线
├── data/                  # 曲库、配置、会话（SharedPreferences）
├── profile/               # 游戏适配（乐器档案、屏幕按键识别）
├── service/               # 无障碍服务 + 悬浮窗（View 实现，见文档 §9）
├── update/                # 热更新与平台接口
└── ui/                    # Compose 界面层
    ├── theme/             #   Material 3 主题 + 顶部渐变底（Backdrop.kt）
    ├── navigation/        #   导航骨架 + 路由（我的/曲库/发现/设置 + 子页面）
    ├── components/        #   SongCard / PlaybackBar / FloatingNavBar / 对话框
    ├── home/              #   我的（首页：竖排时钟 + 左侧导航栏 + 插画 + 悬浮窗开关）
    ├── library/           #   曲库（谱面列表 + 筛选）
    ├── discover/          #   发现 / 搜索 / 平台谱库三页
    ├── importer/          #   导入页（作为曲库的子页面）
    └── settings/          #   设置 / 注册 / 关于 / 校准
```

构建脚本为 Kotlin DSL（`*.gradle.kts`），Kotlin 2.0.21 + Compose 编译器插件。
