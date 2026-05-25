# bilibili-pure-apk — 轻量级 Bilibili 搜索播放器

## 技术栈

| 层 | 选型 |
|---|------|
| 语言 | **Kotlin** |
| UI | **Jetpack Compose** |
| 网络 | **Retrofit + OkHttp** |
| 图片 | **Coil** |
| 视频播放 | WebView (MVP) → ExoPlayer (后续) |
| 构建 | **Gradle KTS** |
| CI | **GitHub Actions** |

## MVP 功能

| 页面 | 功能 |
|------|------|
| **搜索页（首页）** | 搜索框 + 搜索结果列表（视频标题、封面、UP主） |
| **视频详情页** | 标题、播放量、UP主、描述、评论 |
| **播放页** | WebView 嵌入播放 |
| **个人中心** | 占位（后续可加登录） |

## 底部导航

```
搜索 | 个人
```

## 交互流程

```
打开App → 搜索页（输入关键词）→ 点击结果 → 视频详情页 → 点击播放 → WebView播放
```

## 项目结构

```
bilibili-pure-apk/
├── app/
│   ├── src/main/
│   │   ├── java/com/bilibili/pure/
│   │   │   ├── MainActivity.kt
│   │   │   ├── ui/
│   │   │   │   ├── search/          # 搜索页
│   │   │   │   ├── detail/          # 视频详情页
│   │   │   │   ├── player/          # WebView 播放页
│   │   │   │   └── profile/         # 个人中心（占位）
│   │   │   ├── data/
│   │   │   │   ├── api/             # Retrofit 接口定义
│   │   │   │   ├── model/           # 数据类
│   │   │   │   └── repository/      # 数据仓库
│   │   │   └── navigation/          # 导航配置
│   │   └── res/
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradle/wrapper/
│   └── gradle-wrapper.properties
└── .github/
    └── workflows/
        └── build.yml
```

## GitHub Actions 流水线

```yaml
name: Build APK

on:
  push:
    branches: [main]
    tags: ['v*']
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: 'zulu'
          java-version: 17
      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4
      - name: Build Debug APK
        run: ./gradlew assembleDebug
      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: bilibili-pure-debug
          path: app/build/outputs/apk/debug/*.apk
```

- push 到 `main` → 自动构建 debug APK
- 打 `v*` tag → 自动构建 release APK（含签名）
- 构建产物在 Actions 页面可下载

## 后续可扩展

- 原生视频播放器 ExoPlayer
- 用户登录（Cookie/SESSDATA 管理）
- 弹幕显示
- 离线缓存
