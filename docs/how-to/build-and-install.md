# 构建与安装

## 适用场景

你已经拿到源码，想在本机编译出 debug APK 并装到手机上。

## 前置条件

| 项目 | 要求 | 检查命令 |
| --- | --- | --- |
| JDK | 17（其他版本会编译失败） | `java -version` |
| Android SDK | Platform 34 + Platform-Tools（**需自行安装，仓库不附带 SDK**） | `ls $ANDROID_HOME/platforms` |
| 设备 | Android 7.0+，已开启开发者选项与 USB 调试 | `adb devices` |

SDK 可用 Android Studio 的 SDK Manager 安装，或下载 commandline-tools 后执行：

```bash
sdkmanager "platforms;android-34" "platform-tools"
```

## 步骤

### 1. 让 Gradle 找到 SDK

二选一：

```properties
# local.properties（仓库根目录，勿提交）
sdk.dir=C\:\\Users\\<你>\\AppData\\Local\\Android\\Sdk
```

```powershell
$env:ANDROID_HOME = "C:\Users\<你>\AppData\Local\Android\Sdk"
```

同时设置 JDK：

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
```

Linux/macOS 用 `export JAVA_HOME=...` / `export ANDROID_HOME=...`。

> 如果你把 SDK 放在项目目录（例如 `android-sdk/`），`ANDROID_HOME` 指向该目录即可；该目录在 `.gitignore` 中，不会被提交。

### 2. 编译 debug 包

```powershell
.\gradlew.bat :app:assembleDebug
```

预期：`BUILD SUCCESSFUL`，产物在 `app\build\outputs\apk\debug\app-debug.apk`。首次构建会自动下载 Gradle 8.7 与依赖，耗时较长属正常。

### 3. 安装到设备

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

`adb` 在 SDK 的 `platform-tools\` 下；没加进 PATH 时用完整路径 `$env:ANDROID_HOME\platform-tools\adb.exe`。

预期：`Success`。

无线调试（同一局域网、已配对）可先 `adb connect <手机IP>:<端口>`，再用 `adb devices` 确认出现 `device` 状态。

### 4. 验证

```powershell
adb shell am start -n com.bilibili.pure/.MainActivity
```

App 启动到搜索页即为成功。进一步验证日志见 [真机调试与日志](debug-on-device.md)。

## 常见问题

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| `JAVA_HOME is not set` 或 Kotlin 编译报版本错误 | 用了 JDK 8/11/21 | 显式设置 JDK 17 的 `JAVA_HOME` |
| `SDK location not found` | 既没有 `ANDROID_HOME` 也没有 `local.properties` | 按步骤 1 配置其一 |
| `Failed to find target with hash string 'android-34'` | SDK 缺 Platform 34 | `sdkmanager "platforms;android-34"` |
| `adb: command not found` | platform-tools 未加入 PATH | 用 `$env:ANDROID_HOME\platform-tools\adb.exe` |
| `INSTALL_FAILED_VERSION_DOWNGRADE` | 新包 `versionCode` 小于已安装版本 | 提高 `app/build.gradle.kts` 的 `versionCode`（只增不减） |
| `no devices/emulators found` | 未授权 USB 调试或数据线仅充电 | 手机上点「允许 USB 调试」，换数据线，`adb kill-server` 后重试 |
| 装机后「检查更新」提示旧版本 | `versionName` 与 release tag 不一致 | 发布前同步 `app/build.gradle.kts` 的 `versionName` 与 `versionCode` |

## 下一步

- 调 bug、看日志：[真机调试与日志](debug-on-device.md)
- 想改代码前了解分层：[项目结构](../reference/project-structure.md)
