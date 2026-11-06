# 播放与下载实现

## 为什么播放用 DASH、下载用渐进式

播放接口 `x/player/playurl` 有两种形态：

| 形态 | 参数 | 返回 | 用途 |
| --- | --- | --- | --- |
| DASH | `fnval=16` | `dash.video[]` / `dash.audio[]` 多码率分离流 + `accept_quality` | 播放 |
| 渐进式 | `fnval=1` | `durl[]` 单个 MP4 | 下载 |

播放需要「一次拿全清晰度、随时切换」，DASH 正合适：`PlayerViewModel.loadPlayUrlDash()` 保存完整 `PlayUrlInfo`，画质切换时用已有数据重建 `MediaSource`，无需再请求接口（除换清晰度需要新签名的情况）。ExoPlayer 用 `ProgressiveMediaSource` 分别建视频轨与音频轨，再交给 `MergingMediaSource` 合并。

下载需要「一个 URL 直接落盘」，渐进式 MP4 正好；DASH 要额外合并音视频、转封装，成本远高于收益。

## 播放页状态机

```text
进入播放页（默认全屏横屏）
  ├─ isFullscreen = true  → SCREEN_ORIENTATION_SENSOR_LANDSCAPE + 隐藏系统栏
  └─ isFullscreen = false → SCREEN_ORIENTATION_PORTRAIT + 显示系统栏 + 顶部标题栏与分 P 列表
```

- 全屏状态用 Compose state 控制，不新建 Activity，避免旋转重建播放器。
- 控制栏显隐通过 `setControllerVisibilityListener` 同步返回按钮显示。
- `LaunchedEffect(uiState.videoUrl)` 准备媒体源；切换分 P 时按 `resumePositionMs` seek，同一分 P 内重载则沿用 `player.currentPosition`。

## 手势

统一由 `PlayerView.setOnTouchListener` + `GestureDetector` 处理：

| 操作 | 行为 |
| --- | --- |
| 双击 | 播放/暂停（受「仅 WiFi」约束时走二次确认） |
| 长按 | 临时倍速，松手恢复原倍速 |
| 左边缘上下滑 | 调屏幕亮度（`window.attributes.screenBrightness`） |
| 右边缘上下滑 | 调媒体音量 |
| 单击 | 显示/隐藏控制栏 |

触摸顶部状态栏区域会被放行，避免与系统下拉冲突。

## 倍速与画质按钮

`PlayerControlBar` 负责向 `PlayerView` 控制栏注入按钮与 `PopupMenu`，用状态驱动刷新（`update(...)`），不再用 `findViewWithTag` 查找视图：

- 倍速：`SpeedController` 同时维护「按钮倍速」与「长按临时倍速」，两者独立，松手后恢复按钮倍速。
- 画质：`buildQualityList()` 由 `accept_quality`/`accept_description` 生成 `QualityOption`，选择后 `selectQuality()` 重建媒体源并 seek 回原进度。

## 字幕

- `getPlayerInfo(aid, cid)` 取 `subtitle.list`，`subtitle_url` 为相对路径需补 `https:`。
- JSON `body[]` 每项含 `from`/`to`/`content`（秒），`SubtitleParser` 用二分查找定位当前 cue，支持同屏多条。
- `SubtitleOverlay` 用 `BoxWithConstraints` + `offset` 渲染，`onGloballyPositioned` 动态 clamp 边界。
- 拖拽用本地 state 1:1 跟随手指，`LaunchedEffect` 同步到 ViewModel 并持久化坐标（`SubtitlePreferenceManager`）。

## 屏幕常亮

用户反馈（issue #10）播放时屏幕自动熄屏。系统息屏超时只看「最后一次用户输入」，播放本身不算输入，所以必须显式保持常亮。

判断抽在纯函数 `KeepScreenOnLogic.shouldKeepScreenOn(enabled, playWhenReady, playbackState)`：

```text
keepScreenOn = 开关开启 && playWhenReady && 状态 ∉ {IDLE, ENDED}
```

- 开关默认关闭（设置 → 播放设置 → 播放时屏幕常亮），避免改变老用户耗电预期。
- 缓冲中（BUFFERING）仍常亮，避免缓冲时黑屏。
- 暂停、播完、退出播放页立刻恢复系统息屏。
- 实现方式是 `PlayerView.keepScreenOn`，不用 `WakeLock`：无需权限，视图 detach 后窗口标记自动清除。

## 下载

```text
DownloadManager（OkHttp 流式写文件、暂停/续传、SharedPreferences 元数据）
   ↑
DownloadService（前台服务 + 通知，防止后台被杀）
   ↑
DownloadQueueCoordinator（多任务排队与并发控制）
```

- 存储：`getExternalFilesDir("downloads")`，API 29+ 免权限，卸载即清。
- 下载元数据：封面、标题、画质、总大小、已完成字节。
- 本地播放：以 `file://` URI 交给同一个 `PlayerScreen`（`PlaybackSource.LOCAL`），因此字幕、倍速、常亮、进度上报逻辑与在线一致。
- 受「仅 WiFi 下载」设置约束；播放侧对应「仅 WiFi 播放视频」，断网时暂停并 Toast。

## 观看进度上报

服务端 `POST x/v2/history/report`（需登录 + `csrf`，`progress` 单位秒）：

| 时机 | 实现位置 |
| --- | --- |
| 播放中每 30 秒 | `LaunchedEffect(playing...)` 循环 `delay(30_000)` |
| 暂停 | `onIsPlayingChanged` 触发 |
| 退出播放页 | `DisposableEffect.onDispose`，用 `NonCancellable` 防止 ViewModel 取消吞掉请求 |
| 播放结束 / 自动下一 P | `onPlaybackStateChanged(STATE_ENDED)` |

失败不丢数据：`PendingReportManager` 持久化队列（同 `{aid}_{cid}` 去重，上限 20 条），App 启动与进入播放页时 flush；未登录不入队，避免换账号串历史。

## 多 P 进度显示

`x/v2/history` 返回的 `page` 是嵌套对象：

- `duration`：整个视频总时长（所有分 P 之和）
- `progress`：当前分 P 内的进度（非累积）
- `page.duration`：当前分 P 自身时长

进度条分母用 `page.duration`，文本显示 `0:01 / 6:37`，并用 P 标签标明当前分 P。

## 观看历史搜索

历史页搜索走服务端 `GET x/web-interface/history/search`，匹配标题与 UP 主名：

- 返回 `HistorySearchItem`（`title`/`cover`/`author_name`/`author_face`/`history.bvid`），结构与历史列表接口不同。
- `HistoryViewModel.toHistoryItem()` 把搜索结果映射成统一的 `HistoryItem`，UI 只认一种模型。
- `HistoryScreen` 用 `derivedStateOf` 切换数据源：有关键字取 `searchResults`，否则取分页的 `items`。

## 图片缓存

Coil 复用同一个 OkHttp 客户端（带 CDN UA 与 Cookie），内存缓存 16MB LRU、磁盘 50MB。评论 GIF 由 `coil-gif` 解码；大图查看走 Telephoto 并按需降采样，避免 OOM。

## 相关文档

- 选型理由：[架构说明](architecture.md)
- 反爬与 UA 分流：[B 站接口防护](bilibili-api-defense.md)
- 播放器文件清单：[项目结构](../reference/project-structure.md)
