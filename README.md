# Bilibili Pure APK

一个轻量级的第三方 Bilibili Android 客户端，使用 ExoPlayer 原生播放，替换官方的 WebView 播放方案。

## 使用的 API

| API | 端点 | 用途 | 状态 |
|---|---|---|---|
| 搜索 | `https://api.bilibili.com/x/web-interface/search/type` | 视频搜索，支持排序（综合/播放/发布/弹幕/收藏）和分页 | ✅ |
| 视频详情 | `https://api.bilibili.com/x/web-interface/view` | 获取视频信息、封面、分 P 列表 | ✅ |
| 播放地址 | `https://api.bilibili.com/x/player/playurl` | 获取视频 MP4 直链（fnval=1 返回 progressive 格式） | ✅ |
| 评论 | `https://api.bilibili.com/x/v2/comment/main` | 获取视频评论 | ❌ 404 |

## 技术栈

- **播放器**: Media3 ExoPlayer + `media3-datasource-okhttp`（使用自定义 OkHttp 处理 CDN 鉴权）
- **网络**: Retrofit2 + OkHttp（自定义 Interceptor 添加 Referer/User-Agent/Cookie）
- **图片加载**: Coil（使用同一 OkHttp 客户端，确保图片 CDN 鉴权通过）
- **UI**: Jetpack Compose + Material3
- **手势控制**: 横屏全屏模式下左侧滑动调亮度、右侧滑动调音量、长按切换播放倍速（1x/2x/3x）
- **HTTP 协议**: 强制 HTTP/1.1（OkHttp 默认 HTTP/2 会触发 Bilibili WAF 412）

## 已知问题

- 评论接口 `x/v2/comment/main` 返回 404，暂未修复
- CDN 视频地址含 `platform=pc`，需使用桌面版 User-Agent 才能播放
- 搜索 API 需要 `buvid3` cookie，否则 WAF 返回 412
