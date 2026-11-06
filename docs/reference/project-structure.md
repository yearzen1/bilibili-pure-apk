# 项目结构

单模块 Android 应用（`app/`），包名前缀 `com.bilibili.pure`，源码 68 个 Kotlin 文件，按「数据 / UI / 工具」三层组织。

## 包结构

```
app/src/main/java/com/bilibili/pure/
├── BilibiliApp.kt                 # Application：Coil ImageLoader、登录态恢复、离线补传
├── MainActivity.kt                # 单 Activity + Compose NavHost 路由表
├── data/
│   ├── api/                       # Retrofit 接口 + OkHttp 拦截器（UA/Cookie/WBI/限流重试）
│   ├── model/Models.kt            # 全部数据类（搜索/视频/评论/播放/下载/历史…）
│   ├── repository/                # 业务数据入口，聚合多个接口并做降级
│   ├── local/                     # SharedPreferences 封装（设置/历史/进度/字幕/待补传）
│   ├── download/                  # 下载引擎、前台服务、通知、队列协调
│   ├── fav/                       # 收藏夹封面获取
│   └── update/                    # GitHub 版本检查、APK 下载、更新契约
├── ui/
│   ├── navigation/                # Screen / Routes 常量
│   ├── common/                    # 通用组件（搜索框、视频卡片、FAB、文件夹对话框）
│   ├── search/ detail/ channel/   # 搜索、详情、UP 主频道
│   ├── player/                    # 播放页、控制栏、倍速、字幕、常亮判断
│   ├── login/                     # 扫码/短信/密码、极验 WebView
│   ├── history/ favorites/ following/ downloads/ profile/ settings/
│   ├── update/                    # 更新流程状态机与对话框
│   └── theme/
└── util/                          # SubtitleParser、TextUtils 等纯工具
```

## 分层职责

| 层 | 职责 | 约束 |
| --- | --- | --- |
| `data/api` | Retrofit 声明 + 统一网络栈 | 只放接口与拦截器，不写业务判断 |
| `data/repository` | 业务编排、降级（如关注状态三级 fallback） | 返回 `Result`/可空模型，不接触 Android UI |
| `data/local` | 本地持久化与轻量状态 | 统一入口，避免散落 SharedPreferences |
| `ui/*Screen` | Compose 界面与交互 | 不直接发网络请求，通过 ViewModel |
| `ui/*ViewModel` | 状态（StateFlow/Compose State）+ 调用 repository | 不持有 View/Context（除 AndroidViewModel） |
| `util` | 无 Android 依赖的纯函数（解析、格式化、判断） | 必须可 JVM 单测 |

## 关键文件

| 文件 | 作用 |
| --- | --- |
| `data/api/BilibiliApi.kt` | OkHttp 客户端：UA 分流、HTTP/1.1、browser headers、Cookie、WBI 签名、限流与重试 |
| `data/api/PassportApi.kt` | 登录相关接口（二维码、短信、密码、极验） |
| `data/repository/BilibiliRepository.kt` | 全部业务请求编排，含关注/收藏状态查询与进度上报 |
| `data/model/Models.kt` | `PlayUrlInfo`/`DashData`/`QualityOption`/`DownloadInfo`/`HistoryItem` 等 |
| `data/download/DownloadManager.kt` | OkHttp 流式下载、暂停/续传、元数据持久化 |
| `data/download/DownloadService.kt` | 前台服务 + 进度通知 |
| `data/local/PendingReportManager.kt` | 观看进度离线补传队列（最多 20 条） |
| `data/local/AppSettings.kt` | 所有用户设置项读写 |
| `ui/player/PlayerScreen.kt` | 全屏状态机、手势、DASH 合并播放、常亮控制 |
| `ui/player/PlayerViewModel.kt` | 播放地址加载、画质切换、字幕、进度上报 |
| `ui/player/PlayerControlBar.kt` | 控制栏按钮注入（画质/倍速/字幕） |
| `ui/player/KeepScreenOnLogic.kt` | 屏幕常亮纯函数判断 |
| `ui/settings/SettingsScreen.kt` | 设置页：外观/播放/网络/关于 |
| `data/update/UpdateChecker.kt` | GitHub release 比对与版本解析 |
| `data/update/UpdateDownloader.kt` | APK 下载、本地包复用、FileProvider 安装 |

## 关键数据流

### 搜索 → 详情 → 播放

```
SearchScreen → SearchViewModel → BilibiliRepository.searchVideos()
    → Routes.detail(bvid) → DetailScreen → DetailViewModel
        → Routes.player(bvid, cid) → PlayerScreen → PlayerViewModel.load()
            → getPlayUrlDash() (fnval=16) → ExoPlayer MergingMediaSource(视频+音频)
```

### 下载

```
详情页/下载页 → DownloadManager.enqueue()
    → DownloadService（前台服务 + 通知）→ OkHttp 写 externalFilesDir("downloads")
    → SharedPreferences 存元数据 → DownloadsScreen 列表 → 本地 file:// 播放
```

### 观看进度

```
播放中每 30s / 暂停 / 退出 / 切 P
    → PlayerViewModel.reportProgress(aid, cid, progress, duration)
        → 在线：POST x/v2/history/report（需 csrf）
        → 失败：PendingReportManager 入队，App 启动或进入播放页时补传
        → 无论成败：PlaybackProgressManager 本地保存
```

### 应用内更新

```
SettingsScreen「检查更新」→ UpdateChecker（GitHub API 比对 tag）
    → 有新版本：UpdateDownloader.findExisting() 命中本地包则直接安装
    → 否则下载到 externalFilesDir("updates") → FileProvider 拉起系统安装器
```

## 测试

- 单测位于 `app/src/test/java/`，与主代码同包，覆盖纯逻辑（判断、解析、状态机）。
- 无 Robolectric：需要 Android 环境的行为不写单测，改为装机 + logcat 验证。
- 运行：`./gradlew :app:testDebugUnitTest`。

## 相关文档

- 技术选型与分层理由：[架构说明](../explanation/architecture.md)
- 接口细节：[B 站 API 清单](api-endpoints.md)
