# B 站 API 清单

以下是客户端实际调用的接口。域名缩写：`api` = `api.bilibili.com`，`passport` = `passport.bilibili.com`。

## 视频与搜索

| 用途 | 端点 | 鉴权/参数 | 状态 |
| --- | --- | --- | --- |
| 视频搜索 | `api/x/web-interface/search/type` | WBI 签名 + `buvid3` Cookie | ✅ |
| 视频详情 | `api/x/web-interface/view` | — | ✅ |
| UP 主视频列表 | `api/x/space/wbi/arc/search` | WBI 签名 | ✅ |
| 播放地址（MP4） | `api/x/player/playurl` | `fnval=1` | ✅ |
| 播放地址（DASH） | `api/x/player/playurl` | `fnval=16` | ✅ |
| 字幕轨道 | `api/x/player/wbi/v2` | 登录态 | ✅ |
| UP 主信息 | `api/x/web-interface/card` | 弱反爬 | ✅ |
| UP 主空间（备用） | `api/x/space/acc/info` | 反爬严重 | ⚠️ 已被 `card` 替代 |

## 评论与笔记

| 用途 | 端点 | 鉴权/参数 | 状态 |
| --- | --- | --- | --- |
| 评论列表 | `api/x/v2/reply/main` | 游标分页 | ✅ |
| 评论回复 | `api/x/v2/reply/reply` | 最多 20 条 | ✅ |
| 点赞/踩评论 | `api/x/v2/reply/action` | 需 `csrf` | ✅ |

## 登录与账号

| 用途 | 端点 | 鉴权/参数 | 状态 |
| --- | --- | --- | --- |
| 二维码生成 | `passport/x/passport-login/web/qrcode/generate` | — | ✅ |
| 二维码轮询 | `passport/x/passport-login/web/qrcode/poll` | — | ✅ |
| 极验参数 | `passport/x/passport-login/captcha` | 返回 `gt`/`challenge` | ✅ |
| 发送短信验证码 | `passport/x/passport-login/web/sms/send` | `csrf` + 极验结果 | ✅ |
| 短信登录 | `passport/x/passport-login/web/login/sms` | `csrf` + 极验结果 | ✅ |
| 密码登录 | `passport/x/passport-login/web/login` | RSA 加密密码 | ✅ |
| 当前用户信息 | `api/x/web-interface/nav` | 登录态，含 WBI 密钥 | ✅ |

## 历史、收藏、关注

| 用途 | 端点 | 鉴权/参数 | 状态 |
| --- | --- | --- | --- |
| 进度上报 | `api/x/v2/history/report` | 登录 + `csrf`，`progress` 单位秒 | ✅ |
| 观看历史 | `api/x/v2/history` | 登录态 | ✅ |
| 历史搜索 | `api/x/web-interface/history/search` | 登录态 | ✅ |
| 收藏夹列表 | `api/x/v3/fav/folder/created/list-all` | 登录态 | ✅ |
| 收藏夹内容 | `api/x/v3/fav/resource/list` | 登录态 | ✅ |
| 收藏状态 | `api/x/v2/fav/video/favoured` | 登录态 | ✅ |
| 收藏/取消收藏 | `api/x/v3/fav/resource/deal` | POST + `csrf` | ✅ |
| 关注状态 | `api/x/relation` | `attribute`：0 未关注 / 2 已关注 / 6 互粉 | ✅ |
| 关注/取关 | `api/x/relation/modify` | POST + `csrf` | ✅ |
| 关注列表 | `api/x/relation/followings` | 分页，仅登录 | ✅ |

## 约定

- 所有请求由 `BilibiliApi` 的 OkHttp 客户端统一添加 Referer、UA、Cookie 与浏览器头，业务层不重复处理。
- 需要 `csrf` 的写接口，其值必须等于 `bili_jct` Cookie。
- 需要 WBI 签名的接口路径含 `/wbi/`，拦截器自动注入 `w_rid`/`wts`。
- 状态列 ⚠️ 表示仍保留备用实现，但不建议依赖。

## 相关文档

- 反爬与签名原理：[B 站接口防护](../explanation/bilibili-api-defense.md)
- 登录链路：[登录与极验](../explanation/login-and-captcha.md)
