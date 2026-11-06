# 文档导航

本项目文档按 [Diátaxis](https://diataxis.fr/) 分类。当前使用其中三类：**操作指南**（工作+行动）、**参考**（工作+认知）、**解释**（学习+认知）；教程（学习+行动）暂缺，入门直接看操作指南。每篇只服务一种需求。

## 按需求找文档

| 我想… | 文档 | 类型 |
| --- | --- | --- |
| 从零把 App 跑起来 | [构建与安装](how-to/build-and-install.md) | 操作指南 |
| 在真机上看日志、抓崩溃 | [真机调试与日志](how-to/debug-on-device.md) | 操作指南 |
| 查当前有哪些功能和设置项 | [功能清单](reference/features.md) | 参考 |
| 查调用了哪些 B 站接口 | [B 站 API 清单](reference/api-endpoints.md) | 参考 |
| 查代码分几层、文件放哪 | [项目结构](reference/project-structure.md) | 参考 |
| 懂整体架构与技术选型 | [架构说明](explanation/architecture.md) | 解释 |
| 懂 WAF 412 / CDN 403 / WBI 签名 | [B 站接口防护](explanation/bilibili-api-defense.md) | 解释 |
| 懂登录与极验验证码链路 | [登录与极验](explanation/login-and-captcha.md) | 解释 |
| 懂播放器、字幕、下载、历史上报 | [播放与下载实现](explanation/playback-and-downloads.md) | 解释 |

## 目录结构

```
docs/
├── README.md                 # 本文件：导航
├── how-to/                   # 目标明确的操作步骤
│   ├── build-and-install.md
│   └── debug-on-device.md
├── reference/                # 可查证的事实：清单、接口、结构
│   ├── features.md
│   ├── api-endpoints.md
│   └── project-structure.md
├── explanation/              # 为什么这么做、取舍与原理
│   ├── architecture.md
│   ├── bilibili-api-defense.md
│   ├── login-and-captcha.md
│   └── playback-and-downloads.md
└── specs/                    # 内部设计规格（研发流程产物，未纳入版本库）
```

## 维护约定

- 新增文档先判断象限，再按对应骨架写；一篇只占一个象限，混装就拆。
- 功能或接口变动时同步更新 `reference/`，过期事实比没有文档更糟。
- 原理变化时更新 `explanation/`；步骤变化时更新 `how-to/`；入口与导航变化时更新本文件和根 `README.md`。
