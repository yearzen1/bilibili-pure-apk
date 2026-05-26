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
| 登录 | 二维码登录 + 密码登录（WebView 方式） |
| 个人中心 | 登录状态展示、退出登录 |
| 图片加载 | Coil + 自定义 OkHttp，支持 CDN 鉴权 |

## 使用的 API

| API | 端点 | 用途 | 状态 |
|---|---|---|---|
| 搜索 | `x/web-interface/search/type` | 视频搜索，排序与分页 | ✅ |
| 视频详情 | `x/web-interface/view` | 视频信息、封面、分 P | ✅ |
| 播放地址 | `x/player/playurl` | MP4 直链（fnval=1） | ✅ |
| 评论 | `x/v2/reply/main` | 视频评论（游标分页） | ✅ |
| 回复 | `x/v2/reply/reply` | 评论回复（最多 20 条） | ✅ |
| 二维码生成 | `x/passport-login/oauth2/qrcode/generate` | 登录二维码 | ✅ |
| 二维码轮询 | `x/passport-login/oauth2/qrcode/poll` | 扫码状态查询 | ✅ |

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

### 登录
- **二维码登录**: Passport API 生成 → 轮询扫码状态 → 提取 `SESSDATA`/`bili_jct`/`DedeUserID` → SharedPreferences 持久化
- **密码登录**: WebView 加载 `passport.bilibili.com/login` → `onPageFinished` 拦截 Cookie → CookieManager 管理会话
- 应用启动时从 SharedPreferences 恢复登录状态

### Coil 缓存
- 内存缓存：16MB LRU
- 磁盘缓存：50MB

## 已知问题

- `PlayerView.setUseTextureView(true)` 在 Media3 1.4.1 中不可用（需升级），全屏退出时 SurfaceView 偶发闪黑帧
- 评论 API 使用 WBI 签名时返回 -403（需 SESSATA cookie 登录后才可访问）
- WAF 412 在 OkHttp 特定 TLS 握手模式下仍可能偶发触发
