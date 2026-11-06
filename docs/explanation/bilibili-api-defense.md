# B 站接口防护

B 站对 Web 端接口有风控（openresty WAF + CDN 校验 + gaia 反爬），直接照抄浏览器请求也经常被拒。本文解释每种拦截的判别方式与应对，代码集中在 `data/api/BilibiliApi.kt`。

## 四类拦截

| 现象 | 触发方 | 典型响应 | 应对 |
| --- | --- | --- | --- |
| `-412` | WAF 检测 TLS 指纹 | `{"code":-412}` 或 HTTP 412 | 强制 HTTP/1.1 + 浏览器头 + 限流重试 |
| 403 | CDN 校验 UA | 视频/图片加载失败 | 按 URL 切换 UA（见下） |
| `-352` / `-403` | WBI 签名缺失或过期 | gaia 风控 | 注入 `w_rid` / `wts` |
| `-401` | 空间接口反爬 | `crawler_main_space_acc_info` | 换端点或 Safari UA |

## 为什么 HTTP/1.1 能过 WAF

WAF 会检查 TLS 指纹与 HTTP/2 ALPN 特征，OkHttp 默认的 HTTP/2 + 特定密码套件属于「非浏览器」指纹。客户端处理：

- `.protocols(listOf(Protocol.HTTP_1_1))`
- `ConnectionSpec.RESTRICTED_TLS`，只允许 TLS 1.2
- 补齐 `sec-ch-ua*`、`Sec-Fetch-*`、`Accept-Language` 等浏览器头
- `buvid3` / `buvid4` Cookie（部分接口没有直接返回 412）

## UA 三分流

视频地址里带 `platform=pc`，CDN 会拒绝 Android UA；而 API 域反过来要求移动端特征。因此拦截器按 URL 分流：

| 请求类型 | Referer | UA | 附带头 |
| --- | --- | --- | --- |
| CDN（视频/图片） | `www.bilibili.com` | Windows Chrome 120 | 无 sec-ch-ua |
| 普通 API | `www.bilibili.com` | Android Chrome 120（机型来自 `Build`） | Cookie、Accept、Origin |
| 空间 API（`/x/space/*`、`/web-space/`） | `space.bilibili.com` | Safari 15.2 桌面 | 无 sec-ch-ua，不用 WBI |

空间接口用 Chrome UA + `sec-ch-ua` 会触发 gaia `-352`，改用 Safari UA 即可（参考 PiliPlus 的做法）。例外：`/x/space/acc/info` 不走 Safari 分支，因为它本身就反爬严重，业务层已改用 `x/web-interface/card`。

## WBI 签名

需要签名的路径（含 `/wbi/`、`search/type`、`seasons_series_list`、`article/view`）缺 `w_rid` 会返回 `-403`/`-352`。流程：

1. 从 `x/web-interface/nav` 取每日轮换的 `img_key` / `sub_key`（伪装成 PNG 地址），缓存 24 小时。
2. 用固定 64 元素置换表 `MIXIN_KEY_ENC_TAB` 对 `img_key + sub_key` 重排，取前 32 位为 `mixin_key`。
3. 请求参数按 key 排序、`encodeURIComponent` 编码后拼接（过滤 `!'()*`）。
4. 拼上 `mixin_key` 做 MD5 得 `w_rid`，`wts` 为秒级时间戳。
5. 拦截器检测到目标路径后自动注入，业务层无需关心。

注意：`seasons_archives_list` 经验证**不需要** WBI，早期加了签名反而失败。

## 限流与重试

B 站对高频请求会返回 429/412。客户端串行限流 + 指数退避：

- 所有 `api.bilibili.com` / `passport.bilibili.com` 请求间隔不小于 300ms。
- 429 从 1s 起、412 从 2s 起指数退避，最多重试 3 次；若响应带 `Retry-After` 则以其为准。

这也解释了为什么批量接口（关注列表分页、补传队列）不会打爆风控。

## 快速判别

| 你看到的 | 大概率原因 | 下一步 |
| --- | --- | --- |
| 搜索/空间接口 `-352` | UA 或签名不对 | 确认是否走 Safari 分支、是否注入 WBI |
| 视频/图片 403 | 用了 Android UA 请求 CDN | 检查 URL 是否被判为 CDN |
| 全接口 `-412` | TLS/HTTP2 指纹或缺少 `buvid3` | 检查协议与 Cookie 初始化时机 |
| `-401 crawler_main_space_acc_info` | 空间 acc/info 反爬 | 改用 `x/web-interface/card` |

## 延伸阅读

- 接口与鉴权速查：[B 站 API 清单](../reference/api-endpoints.md)
- 登录相关 Cookie：[登录与极验](login-and-captcha.md)
