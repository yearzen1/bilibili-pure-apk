# 真机调试与日志

## 适用场景

App 已安装到设备，需要抓日志定位崩溃、播放失败、接口报错等问题。

## 前置条件

- 设备已连接：`adb devices` 显示 `device`（USB 或 `adb connect` 无线连接）
- 包名：`com.bilibili.pure`

## 常用命令

设 `$adb = ".\android-sdk\platform-tools\adb.exe"` 后按需执行。

| 目的 | 命令 |
| --- | --- |
| 只看本 App 全部日志 | `$adb logcat --pid=$($adb shell pidof -s com.bilibili.pure)` |
| 只看 Error | `$adb logcat *:E --pid=...` |
| 看崩溃缓冲区 | `$adb logcat -b crash --pid=...` |
| 按 TAG 过滤 | `$adb logcat -s BiliPure:V --pid=...` |
| 清空后重新抓 | `$adb logcat -c`，再执行上面的命令 |
| 导出到文件 | `$adb logcat --pid=... > log.txt` |
| 截图 | `$adb exec-out screencap -p > shot.png` |

TAG 统一为 `BiliPure`（见 `BilibiliApp.TAG`），debug 包才会输出详细日志。

## 关键日志与含义

| 关键字 | 含义 |
| --- | --- |
| `Player: state=READY/BUFFERING/ENDED` | ExoPlayer 状态机流转 |
| `Player: isPlaying=true/false` | 播放/暂停切换 |
| `Player: keepScreenOn=true/false` | 屏幕常亮开关是否生效（需在设置里开启「播放时屏幕常亮」） |
| `periodic REPORT` / `pause REPORT` / `onDispose REPORT` | 观看进度上报时机 |
| `PlayerScreen: entering bvid=... source=ONLINE/LOCAL` | 进入播放页及其来源 |

## 验证一次改动的通用流程

1. `adb logcat -c` 清空日志
2. 在设备上复现操作
3. 抓取对应 TAG 日志，确认无 `FATAL EXCEPTION`、`Player: error=`
4. 需要复现熄屏类问题时，用 `adb shell settings put system screen_off_timeout 15000` 缩短息屏时间，测完还原（`2147483647` 表示永不熄屏）

## 常见问题

| 现象 | 处理 |
| --- | --- |
| `--pid` 为空导致 logcat 报错 | App 未在前台运行，先 `am start -n com.bilibili.pure/.MainActivity` |
| 日志刷屏难以定位 | 用 `-s BiliPure:V` 或 `*:E` 过滤，必要时再配合 `Select-String` 搜索关键字 |
| 无线调试反复断开 | 重新 `adb connect`，或在开发者选项里重新配对 |

## 下一步

- 复现的是接口类问题：[B 站接口防护](../explanation/bilibili-api-defense.md) 里列了 412/403/-352 的判别方法
- 复现的是播放问题：[播放与下载实现](../explanation/playback-and-downloads.md)
