# Bilibili Pure APK

轻量级第三方 Bilibili Android 客户端：用 Media3 ExoPlayer 原生播放替换官方 WebView 播放器，没有推荐流和广告，只保留搜索、播放、收藏这些核心功能。

[最新版本](https://github.com/yearzen1/bilibili-pure-apk/releases/latest) · [问题反馈](https://github.com/yearzen1/bilibili-pure-apk/issues) · [文档导航](docs/README.md)

## 能做什么

- **搜索**：5 种排序（综合/播放/发布/弹幕/收藏）、左右滑动切排序、搜索历史
- **详情**：多 P 选集、合集、收藏夹选择、关注 UP 主、评论（GIF/表情/图片查看/笔记）
- **播放**：原生 ExoPlayer、DASH 多画质切换、0.5x–2.0x 倍速、手势控制、AI/CC 字幕、全屏状态栏、屏幕常亮开关
- **下载**：渐进式 MP4、暂停/续传/删除、前台服务通知、应用私有目录免权限、本地播放
- **账号**：扫码/短信/密码登录、观看历史（心跳上报+离线补传）、我的收藏、关注列表
- **其他**：深色模式、应用内更新、用户反馈邮件

完整功能与设置项清单见 [功能清单](docs/reference/features.md)。

## 快速开始

环境：JDK 17、Android SDK 34（仓库内已带 `android-sdk/`）。

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Windows 下需先设置 `JAVA_HOME` / `ANDROID_HOME`，完整步骤与常见报错见 [构建与安装](docs/how-to/build-and-install.md)。

## 文档

文档按 [Diátaxis](https://diataxis.fr/) 分为操作指南 / 参考 / 解释三类，按需求查：

| 我想… | 入口 |
| --- | --- |
| 学：把项目跑起来 | [构建与安装](docs/how-to/build-and-install.md) |
| 做：在真机上调 bug | [真机调试与日志](docs/how-to/debug-on-device.md) |
| 查：有哪些功能/接口/文件 | [docs/reference](docs/reference/features.md) |
| 懂：为什么这样实现 | [docs/explanation](docs/explanation/architecture.md) |

全部文档索引见 [docs/README.md](docs/README.md)。

## 兼容性

Android 7.0+（`minSdk = 24`）。日常在小米/红米设备验证，另在 Android 15 模拟器上跑通。

## 免责声明

本项目仅供学习与个人使用，与哔哩哔哩官方无关，不得用于商业用途。所有数据来自 B 站公开 Web 接口，接口可能随时变更导致功能失效。

## 许可

[MIT](LICENSE) © 2026 yearzen1
