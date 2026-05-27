# Bilibili Pure APK

一个轻量级的第三方 Bilibili Android 客户端，使用 ExoPlayer 原生播放视频，替换官方 WebView 播放方案。

## 功能

| 功能 | 说明 |
|---|---|
| 视频搜索 | 支持 5 种排序（综合/播放/发布/弹幕/收藏）、分页加载、左右滑动切换排序 |
| 视频详情 | 多 P 选集、描述展开/折叠、发布日期、封面 |
| 原生播放 | ExoPlayer 播放 MP4 直链，支持全屏（传感器横屏）、手势控制 |
| 手势控制 | 双击播放/暂停、长按倍速（松手恢复 1x）、左侧调亮度、右侧调音量 |
| 速度控制 | 弹出菜单（0.5x–2.0x）、长按快速切换 |
| 评论系统 | 分页加载、展开回复、收起回复 |
| UP主频道 | 视频详情页点击 UP主名进入频道页，查看该 UP主的所有视频（分页） |
| 登录 | 二维码登录 + 密码登录（WebView 方式） |
| 个人中心 | 登录状态展示、退出登录 |
| 图片加载 | Coil + 自定义 OkHttp，支持 CDN 鉴权 |
| 观看历史 | 30 秒心跳上报播放进度到 B 站服务器，暂停/退出/切 P 时同步 |

## 使用的 API

| API | 端点 | 用途 | 状态 |
|---|---|---|---|
| 搜索 | `api.bilibili.com/x/web-interface/search/type` | 视频搜索，排序与分页 | ✅ |
| 视频详情 | `api.bilibili.com/x/web-interface/view` | 视频信息、封面、分 P | ✅ |
| UP主视频列表 | `api.bilibili.com/x/space/wbi/arc/search` | UP主视频分页列表（WBI 签名） | ✅ |
| 播放地址 | `api.bilibili.com/x/player/playurl` | MP4 直链（fnval=1） | ✅ |
| 评论 | `api.bilibili.com/x/v2/reply/main` | 视频评论（游标分页） | ✅ |
| 回复 | `api.bilibili.com/x/v2/reply/reply` | 评论回复（最多 20 条） | ✅ |
| 二维码生成 | `api.bilibili.com/x/passport-login/oauth2/qrcode/generate` | 登录二维码 | ✅ |
| 二维码轮询 | `api.bilibili.com/x/passport-login/oauth2/qrcode/poll` | 扫码状态查询 | ✅ |
| 进度上报 | `api.bilibili.com/x/v2/history/report` | 心跳上报播放进度（需 csrf） | ✅ |
| 观看历史 | `api.bilibili.com/x/v2/history` | 视频历史记录 | ✅ |

## 技术栈

- **播放器**: Media3 ExoPlayer 1.4.1 + `media3-datasource-okhttp`
- **网络**: Retrofit2 + OkHttp 4.12.0（自定义拦截器添加 Referer/UA/Cookie/浏览器头）
- **图片加载**: Coil 2.6.0（复用同一 OkHttp 客户端处理 CDN 鉴权）
- **UI**: Jetpack Compose + Material3 + Compose BOM 2024.04.01
- **手势**: `Modifier.pointerInput` + `awaitPointerEventScope`（搜索滑动切排序用 `PointerEventPass.Initial`）
- **二维码**: ZXing 3.5.3

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
- **二维码登录**: Passport API 生成 → 轮询扫码状态 → 提取 `SESSDATA`/`bili_jct`/`DedeUserID` → SharedPreferences 持久化
- **密码登录**: WebView 加载 `passport.bilibili.com/login` → `onPageFinished` 拦截 Cookie → CookieManager 管理会话
- 应用启动时从 SharedPreferences 恢复登录状态

### 观看历史 / 心跳上报
通过 `POST x/v2/history/report` 定时上报播放进度，使视频出现在 B 站观看历史中：
- **播放中**：每 30 秒上报一次（`LaunchedEffect` + `delay(30_000)` 循环）
- **暂停**：`onIsPlayingChanged` 触发即时上报
- **退出**：`DisposableEffect.onDispose` 在释放播放器前上报最终进度
- **切 P**：切换分 P 前上报当前分 P 的进度
- **身份认证**：需登录（`SESSDATA`/`bili_jct`/`DedeUserID`），且 `csrf` 表单字段必须等于 `bili_jct` cookie 值
- **progress 单位**：秒（`player.currentPosition / 1000`，最小 1）

### Coil 缓存
- 内存缓存：16MB LRU
- 磁盘缓存：50MB

## 已知问题

- `PlayerView.setUseTextureView(true)` 在 Media3 1.4.1 中不可用（需升级），全屏退出时 SurfaceView 偶发闪黑帧
- 评论 API 仍返回 -403（部分端点还需 WBI 签名 + SESSDATA 登录才能访问）
- WAF 412 在 OkHttp 特定 TLS 握手模式下仍可能偶发触发
