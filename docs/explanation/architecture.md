# 架构说明

## 问题背景

B 站官方客户端的播放页依赖 WebView + H5 播放器，功能齐全但体积大、耗电高、交互卡顿。本项目只想要「搜索 → 看视频」这条主链路，于是用原生 Compose + ExoPlayer 重写界面与播放，共用 B 站 Web 端接口。

## 技术栈与选型理由

| 领域 | 选择 | 为什么 |
| --- | --- | --- |
| 播放器 | Media3 ExoPlayer 1.4.1 + `media3-exoplayer-dash` | 原生解码、支持 DASH 多轨合并、可精确控制手势与倍速；Media3 是官方维护的继任者 |
| 网络 | Retrofit2 + OkHttp 4.12 | 拦截器可集中处理 B 站要求的 Cookie/UA/签名；复用同一客户端给 Coil |
| 图片 | Coil 2.6（自定义 OkHttp） | 直接复用带鉴权头的客户端，避免 CDN 图片 403；`coil-gif` 支持评论 GIF |
| 图片查看 | Telephoto 0.12 | 评论大图缩放与拖拽成熟，避免自己处理多点触控 |
| UI | Jetpack Compose + Material3 | 状态驱动，播放器控制栏可用状态更新而非 `findViewWithTag` |
| 导航 | Navigation Compose | 单 Activity 路由表，参数集中在 `Routes` |
| 二维码 | ZXing 3.5 | 扫码登录生成二维码 |
| 下载 | OkHttp 流式写文件 + 前台服务 | 不需要额外下载库；前台服务保证后台不被杀 |

## 分层

```text
Compose Screen  ──观察状态──▶  ViewModel  ──调用──▶  Repository  ──▶  Retrofit/OkHttp
     │                           │                      │
     └──用户事件──────────────────┘                 data/local（持久化）
```

- **Screen 不碰网络**：所有请求经 ViewModel → Repository，屏幕只负责渲染与事件。
- **Repository 做降级**：B 站部分接口有反爬（如 `space/acc/info` 返回 `-401`），Repository 里实现三级 fallback，UI 无感知。
- **纯逻辑可测**：判断、解析、状态机抽成无 Android 依赖的函数（如 `KeepScreenOnLogic`、`CommentScrollAnchor`、`SubtitleParser`），配 JVM 单测。

## 关键取舍

### 原生播放 vs WebView

代价是需要自己实现选集、画质、字幕、倍速与进度上报；收益是启动快、手势跟手、可后台下载。B 站播放地址接口足够稳定，因此选择原生。

### DASH 用于播放，渐进式用于下载

播放走 `fnval=16`（DASH）：一次拿到全部清晰度，画质切换无需重新请求页面，`MergingMediaSource` 合并视频轨与音频轨。下载走 `fnval=1`（渐进式 MP4）：单 URL 直接落盘，省去音视频合并与转封装。

### 单 Activity + Compose 导航

全屏播放用状态机（`isFullscreen`）而非独立 Activity：避免旋转重建播放器、避免 SurfaceView 退出闪屏。返回键与系统栏由 `LaunchedEffect(isFullscreen)` 统一控制。

### 存储全部使用应用私有目录

下载与更新包写入 `getExternalFilesDir(...)`：API 29+ 免存储权限，卸载即清理，不需要 SAF 交互。代价是文件不进入系统「下载」目录。

### 不引入 WorkManager

离线补传只在 App 启动与进入播放页时 flush（`PendingReportManager`，上限 20 条）。心跳本身 30 秒一次，错过几条对观看历史影响可接受，换来零额外依赖。

### 无 Robolectric

依赖 Android 环境的行为（播放器、WebView、SharedPreferences）不写单测，改由装机 + `adb logcat` 验证；纯逻辑全部单测覆盖。这是精度与成本的折中。

## 数据与状态

- **登录态**：`SESSDATA` / `bili_jct` / `DedeUserID` 存 SharedPreferences，`BilibiliApp` 启动时恢复。
- **设置**：`AppSettings` 单例封装 `bili_settings`，读写集中，部分项用 Compose `mutableStateOf` 承载以便即时重组。
- **播放进度**：服务端（`x/v2/history/report`）+ 本地（`PlaybackProgressManager`）双写，失败入队补传。
- **列表状态**：ViewModel 内 `StateFlow`/Compose State，分页游标与去重逻辑在 ViewModel 完成（如搜索结果按 BVID 去重）。

## 延伸阅读

- 具体接口与反爬：[B 站接口防护](bilibili-api-defense.md)
- 播放、字幕、下载细节：[播放与下载实现](playback-and-downloads.md)
- 文件与包清单：[项目结构](../reference/project-structure.md)
