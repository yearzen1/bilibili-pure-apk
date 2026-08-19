

# Bilibili Pure APK

一个轻量级的第三方 Bilibili Android 客户端，使用 ExoPlayer 原生播放视频，替换官方 WebView 播放方案。

## 功能

| 功能 | 说明 |
|---|---|
| 视频搜索 | 支持 5 种排序（综合/播放/发布/弹幕/收藏）、分页加载、左右滑动切换排序、搜索历史记录持久化 |
| 视频详情 | 多 P 选集、描述展开/折叠、发布日期、封面、收藏按钮（心形切换，仅登录显示） |
| 原生播放 | ExoPlayer 播放 MP4 直链，支持全屏（传感器横屏）、手势控制 |
| 手势控制 | 双击播放/暂停、长按倍速（松手恢复 1x）、左侧调亮度、右侧调音量 |
| 速度控制 | 弹出菜单（0.5x–2.0x）、长按快速切换 |
| 评论系统 | 分页加载、展开回复、收起回复 |
| UP主频道 | 视频详情页点击 UP主名进入频道页，查看该 UP主的所有视频（分页） |
| 登录 | 三标签页登录：扫码 / 短信 / 密码（原生 UI），极验 Geetest 验证码，`onJsPrompt` JS↔Native 桥接 |
| 画质选择 | DASH（fnval=16）多画质流，ExoPlayer `MergingMediaSource` 合并视频音频，播放器内 PopupMenu 切换 |
| 视频下载 | `DownloadManager` 下载渐进式 MP4（fnval=1），前台服务通知、暂停/继续/删除、应用私有目录免权限 |
| 我的下载 | 下载列表（封面/标题/画质/大小/进度），支持本地文件播放 |
| 个人中心 | 登录状态展示（头像/昵称/UID）、我的下载/设置入口、退出登录 |
| 设置 | 仅 Wi-Fi 开关：播放与下载（非 Wi-Fi 时 Toast 提示并阻止） |
| 图片加载 | Coil + 自定义 OkHttp，支持 CDN 鉴权 |
| 观看历史 | 分 P 进度显示、30 秒心跳上报、全量缓存标题搜索、回到顶部按钮 |
| 我的收藏 | 收藏夹列表 → 视频列表两级浏览、分页加载、BackHandler 拦截系统返回手势 |
| 搜索历史 | 保存最近 10 条搜索记录到本地 SharedPreferences，支持单条删除和清空 |
| 关注 UP 主 | 视频详情页和 UP 主频道页关注/取关按钮（仅登录显示），`x/relation` 直查关注状态 |
| 关注列表 | 个人中心「我的关注」入口，分页加载，支持横向滑动排序切换 |

## 使用的 API

| API | 端点 | 用途 | 状态 |
|---|---|---|---|
| 搜索 | `api.bilibili.com/x/web-interface/search/type` | 视频搜索，排序与分页 | ✅ |
| 视频详情 | `api.bilibili.com/x/web-interface/view` | 视频信息、封面、分 P | ✅ |
| UP主视频列表 | `api.bilibili.com/x/space/wbi/arc/search` | UP主视频分页列表（WBI 签名） | ✅ |
| 播放地址 | `api.bilibili.com/x/player/playurl` | MP4 直链（fnval=1） | ✅ |
| 播放地址(DASH) | `api.bilibili.com/x/player/playurl` | 多画质视频+音频流（fnval=16） | ✅ |
| 评论 | `api.bilibili.com/x/v2/reply/main` | 视频评论（游标分页） | ✅ |
| 回复 | `api.bilibili.com/x/v2/reply/reply` | 评论回复（最多 20 条） | ✅ |
| 二维码生成 | `passport.bilibili.com/x/passport-login/web/qrcode/generate` | 登录二维码 | ✅ |
| 二维码轮询 | `passport.bilibili.com/x/passport-login/web/qrcode/poll` | 扫码状态查询 | ✅ |
| 极验验证 | `passport.bilibili.com/x/passport-login/captcha` | 获取 Geetest token/challenge/gt | ✅ |
| 发送验证码 | `passport.bilibili.com/x/passport-login/web/sms/send` | 短信验证码发送 | ✅ |
| 短信登录 | `passport.bilibili.com/x/passport-login/web/login/sms` | 验证码登录 | ✅ |
| 密码登录 | `passport.bilibili.com/x/passport-login/web/login` | 用户名+RSA加密密码登录 | ✅ |
| 进度上报 | `api.bilibili.com/x/v2/history/report` | 心跳上报播放进度（需 csrf） | ✅ |
| 观看历史 | `api.bilibili.com/x/v2/history` | 视频历史记录（返回 `HistoryPage` 对象含 `page`/`part`/`duration`） | ✅ |
| 收藏夹列表 | `api.bilibili.com/x/v3/fav/folder/created/list-all` | 获取用户收藏夹列表（需登录） | ✅ |
| 收藏夹内容 | `api.bilibili.com/x/v3/fav/resource/list` | 收藏夹内视频资源分页（需登录） | ✅ |
| 收藏状态 | `api.bilibili.com/x/v2/fav/video/favoured` | 查询当前用户是否已收藏视频（需登录） | ✅ |
| 收藏/取消收藏 | `api.bilibili.com/x/v3/fav/resource/deal` | 添加/移出收藏夹（POST，需 csrf） | ✅ |
| 关注状态 | `api.bilibili.com/x/relation` | 查询当前用户与目标 UP 主的关注关系（attribute: 0/2/6） | ✅ |
| 关注/取关 | `api.bilibili.com/x/relation/modify` | 关注或取消关注 UP 主（POST，需 csrf） | ✅ |
| 关注列表 | `api.bilibili.com/x/relation/followings` | 获取用户关注列表（分页，仅登录用户查看全部） | ✅ |
| 当前用户信息 | `api.bilibili.com/x/web-interface/nav` | 登录用户昵称/头像/UID，含 WBI 密钥 | ✅ |
| UP 主信息 | `api.bilibili.com/x/web-interface/card` | UP 主头像、昵称、签名、关注状态（弱反爬） | ✅ |
| UP 主空间 | `api.bilibili.com/x/space/acc/info` | 备用端点（反爬严重，已被 `card` 替代） | ⚠️ |

## 技术栈

- **播放器**: Media3 ExoPlayer 1.4.1 + `media3-exoplayer-dash` + `media3-datasource-okhttp`
- **网络**: Retrofit2 + OkHttp 4.12.0（自定义拦截器添加 Referer/UA/Cookie/浏览器头）
- **图片加载**: Coil 2.6.0（复用同一 OkHttp 客户端处理 CDN 鉴权）
- **UI**: Jetpack Compose + Material3 + Compose BOM 2024.04.01
- **手势**: `Modifier.pointerInput` + `awaitPointerEventScope`（搜索滑动切排序用 `PointerEventPass.Initial`）
- **二维码**: ZXing 3.5.3
- **下载**: 前台服务 + 通知 + OkHttp 流式写入 + SharedPreferences 元数据

## 关键实现细节

### WAF 412 防护
Bilibili WAF（openresty）会检测 OkHttp 的 TLS 指纹并返回 412。防护措施：
- 强制 HTTP/1.1（避免 HTTP/2 ALPN 指纹检测）
- 添加浏览器头：`sec-ch-ua`、`sec-ch-ua-mobile`、`sec-ch-ua-platform`、`Sec-Fetch-*`
- 自定义 ConnectionSpec：限制 TLS 1.2 及特定密码套件
- `buvid3` Cookie 用于 API 鉴权

### CDN 403 防护
Bilibili CDN 视频地址含 `platform=pc`，拒绝 Android UA。拦截器根据 URL 自动切换：
- **CDN 请求** → 桌面 Windows Chrome UA
- **API 请求** → Android Chrome UA + 完整浏览器头

### WBI 签名
Bilibili Web 端部分接口（如 `x/space/wbi/arc/search`）需要 WBI 签名鉴权，缺失 `w_rid`/`wts` 时返回 -403 或 -352。

签名流程：
1. 从 `x/web-interface/nav` 获取每日轮换的 `img_key`/`sub_key`（伪装成 PNG URL，缓存 24 小时）
2. 用固定 64 元素置换表 `MIXIN_KEY_ENC_TAB` 对 `img_key + sub_key` 打乱重排，取前 32 位为 `mixin_key`
3. 参数按 key 排序，`encodeURIComponent` 编码后拼接（过滤 `!'()*`）
4. 拼接 `mixin_key` 后 MD5 → `w_rid`，`wts` 为 Unix 秒级时间戳
5. 拦截器自动检测 `/wbi/` 路径，注入签名参数

### 登录
三标签页（扫码 / 短信 / 密码），登录成功提取 `SESSDATA`/`bili_jct`/`DedeUserID` 存入 SharedPreferences，启动时恢复。

- **扫码登录**: Passport API 生成二维码 → 轮询扫码确认 → 从回调 URL 提取会话 Cookie（天然绕过网页风控，推荐）
- **短信/密码登录**: 原生 Compose UI + `x/passport-login/captcha` 获取 Geetest `gt`/`challenge`，WebView 内嵌极验 → JS 结果经 `onJsPrompt` 桥接回传
- **极验集成要点**:
  - `gt` 与 `challenge` 必须使用接口动态返回值（写死会导致极验初始化静默失败）
  - 极验 `getValidate()` 返回字段带前缀：`geetest_challenge`/`geetest_validate`/`geetest_seccode`
  - 传给登录/发短信接口的 `challenge` 用极验返回的 `geetest_challenge`，而非最初获取的 challenge
  - JS→Native 用 `WebChromeClient.onJsPrompt` 桥接（`prompt(type+':'+JSON)`），比 `addJavascriptInterface`/轮询更可靠
- 登录请求需带浏览器头 + buvid3 Cookie，故拦截器将 `passport.bilibili.com` 视为 API 域（而非 CDN）
- **风险控制**: B 站对新 IP/设备触发环境风控，密码/短信网页登录可能被拦（返回 `data.status=2` +「本次登录环境存在风险」且不下发会话 Cookie）。日志/Toast 会展示服务器 `data.message` 提示真实原因。

### 画质选择（DASH）
播放用 `fnval=16`（DASH）换取全部清晰度流：
- `getPlayUrlDash()` 返回完整 `PlayUrlInfo`，含 `accept_quality`/`accept_description` 及 `dash.video`/`dash.audio` 流
- ExoPlayer 以 `ProgressiveMediaSource` + `MergingMediaSource` 合并视频/音频轨
- 播放器控制栏注入画质 PopupMenu（同倍速按钮方案），切换后重新构建 `MediaItem` 并 seek 回原进度
- 下载则用 `fnval=1`（渐进式 MP4），单 URL 便于直接落盘

### 视频下载
`DownloadManager` 基于 OkHttp 流式下载渐进式 MP4：
- 存储到 `context.getExternalFilesDir(null)` 下的 `downloads/` 目录（应用私有，API 29+ 免权限，卸载即清）
- SharedPreferences 持久化元数据（封面/标题/画质/大小）
- `DownloadService` 前台服务 + 进度通知，支持暂停/继续/删除
- 本地文件以 `file://` URI 交给播放器播放
- 下载受「仅 Wi-Fi」设置约束

### 观看历史 / 心跳上报
通过 `POST x/v2/history/report` 定时上报播放进度，使视频出现在 B 站观看历史中：
- **播放中**：每 30 秒上报一次（`LaunchedEffect` + `delay(30_000)` 循环）
- **暂停**：`onIsPlayingChanged` 触发即时上报
- **退出**：`DisposableEffect.onDispose` 在释放播放器前上报最终进度
- **切 P**：切换分 P 前上报当前分 P 的进度
- **身份认证**：需登录（`SESSDATA`/`bili_jct`/`DedeUserID`），且 `csrf` 表单字段必须等于 `bili_jct` cookie 值
- **progress 单位**：秒（`player.currentPosition / 1000`，最小 1）

### 观看历史搜索
通过 Repository 全量缓存实现本地搜索，避免多 P 状态污染：

1. `BilibiliRepository.getAllHistory()` 内部缓存所有分页数据（`fullHistoryCache`），仅首次搜索时请求一次
2. `HistoryViewModel` 维护两个独立列表：
   - `items` — 分页浏览（`loadHistory` + `loadMore` 维护）
   - `searchResults` — 搜索结果（`searchInHistory` 唯一写入点）
3. `HistoryScreen` 通过 `derivedStateOf` 切换 `displayItems`：搜索时取 `searchResults`，否则取 `items`
4. 清空搜索时 `searchResults = emptyList`，`items` 不受影响，分页继续正常加载

### 多 P 进度显示
Bilibili 历史 API 返回的 `page` 字段为嵌套对象 `HistoryPage(page, part, duration, cid)`：
- `duration` - 视频总时长（所有分 P 之和）
- `progress` - 当前分 P 内的播放进度（非累积）
- `page.duration` - 当前分 P 的单独时长
- 进度条以当前分 P 时长 `page.duration` 为分母，进度文本显示 `0:01 / 6:37`，上方 P 标签标识当前分 P

### Coil 缓存
- 内存缓存：16MB LRU
- 磁盘缓存：50MB

## 环境要求

| 项目 | 版本 |
|---|---|
| JDK | 17 |
| Android SDK | 34（已内置在 `android-sdk/`） |
| Gradle | 8.7（wrapper 自动下载） |
| 设备 | Android 7.0+（`minSdk = 24`） |

```bash
# 构建
./gradlew assembleDebug

# 安装（需连接设备并开启 USB 调试）
android-sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 已知问题

- `PlayerView.setUseTextureView(true)` 在 Media3 1.4.1 中不可用（需升级），全屏退出时 SurfaceView 偶发闪黑帧
- 评论 API 仍返回 -403（部分端点还需 WBI 签名 + SESSDATA 登录才能访问）
- 密码/短信网页登录受 B 站环境风控影响，新 IP 设备可能被拦截（建议使用扫码登录）
- WAF 412 在 OkHttp 特定 TLS 握手模式下仍可能偶发触发
