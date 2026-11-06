# 登录与极验

## 为什么登录要做三套

B 站 Web 端登录有三条路径，风控强度依次升高：

| 方式 | 入口 | 风控 | 体验 |
| --- | --- | --- | --- |
| 扫码 | `qrcode/generate` + `qrcode/poll` | 最弱（相当于手机 App 扫码） | 需要另一个已登录设备，最稳 |
| 短信 | `sms/send` + `login/sms` | 中（需极验） | 单设备可用 |
| 密码 | `login` | 最高（需极验 + 环境校验） | 新设备/新 IP 常被拦 |

因此扫码是推荐路径，短信/密码是备用。

## 三条链路的共同终点

无论走哪条，最终都要拿到三个 Cookie：

| Cookie | 用途 |
| --- | --- |
| `SESSDATA` | 会话凭证，所有需要登录的接口都靠它 |
| `bili_jct` | CSRF Token，写接口的 `csrf` 字段必须等于它 |
| `DedeUserID` | 用户 UID |

扫码登录从轮询返回的 `url` 查询参数中直接提取；短信/密码登录由接口下发会话 Cookie。拿到后写入 `BilibiliApi.setLoginCookies(...)` 与 SharedPreferences（`bili_prefs`），`BilibiliApp` 启动时恢复，因此重启无需重新登录。

## 扫码登录时序

```text
generateQRCode() → 显示二维码（qrcodeKey）
   ↓ 每 1500ms poll
0      → 成功：从回调 URL 提取 SESSDATA/bili_jct/DedeUserID
86090  → 已扫码待确认（界面提示）
86038  → 二维码过期（提示重试）
```

扫码路径绕过了网页风控，是默认方案。

## 极验（Geetest）集成

短信与密码登录前需要极验 token，流程是「原生 UI 发起 → WebView 完成挑战 → 结果回传 native」：

1. `x/passport-login/captcha` 获取 `gt` 与 `challenge`。
   **必须使用接口动态返回值**：写死会导致极验初始化静默失败，界面空白无报错。
2. `GeetestCaptcha` 用 `loadDataWithBaseURL("https://www.bilibili.com", html, ...)` 加载内嵌极验的 HTML。
3. 极验成功后，页面里的 JS 通过 `prompt(type + ':' + JSON.stringify(data))` 回调。
4. `WebChromeClient.onJsPrompt` 捕获字符串，按 `:` 拆分 `type` 与 JSON，解析出验证结果。
5. 回传字段带前缀：`geetest_challenge` / `geetest_validate` / `geetest_seccode`。
6. 调用登录/发短信接口时，`challenge` 用极验返回的 `geetest_challenge`，**不是**最初获取的那个 `challenge`。

为什么用 `onJsPrompt` 而不是 `addJavascriptInterface`：后者在 Android 4.2+ 需要 `@JavascriptInterface` 注解且存在注入风险，轮询方案又会引入时序问题；`prompt` 是同步桥接，最可靠。

WebView 必须在 `DisposableEffect(onDispose)` 中 `destroy()`，否则每次进登录页都会泄漏一个 WebView 实例。

## 环境风控

新 IP / 新设备用密码或短信登录时，可能返回 `data.status = 2` 与「本次登录环境存在风险」，且不下发会话 Cookie。这不是代码 bug，客户端能做的只有：

- 把服务端 `data.message` 原样展示给用户（日志与 Toast）
- 提示改用扫码登录

## 相关文档

- 接口与参数：[B 站 API 清单](../reference/api-endpoints.md)
- 为什么登录接口也要浏览器头：[B 站接口防护](bilibili-api-defense.md)
