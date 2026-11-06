---
name: conventional-commit
description: 写提交信息、拆分原子提交、或 review 提交历史时套用 Conventional Commits。含本仓库的 type/scope 清单、72 字符与中文正文约定、提交前自查清单。Use when writing or reviewing git commit messages.
---

# Conventional Commits

## 1. 格式

```text
<type>(<scope>): <subject>
<空行>
<正文>
<空行>
<footer>
```

- `scope` 可省略：`docs: ...` 合法。
- 破坏性变更在 type 后加 `!`：`feat(cli)!: drop the upgrade flag`，并在 footer 写 `BREAKING CHANGE: <说明>`。

## 2. type 白名单

只允许这些，自造 type（`feature`、`update`、`improvement`）一律不认：

| type | 用途 |
|---|---|
| `feat` | 用户可见的新能力 |
| `fix` | 修正错误行为 |
| `docs` | 只改文档、注释、README |
| `test` | 只加/改测试 |
| `refactor` | 不改行为的结构调整 |
| `perf` | 性能 |
| `style` | 格式、空白，不改语义 |
| `build` | 依赖、构建脚本、版本号 |
| `ci` | 流水线配置 |
| `chore` | 杂务（gitignore、脚手架） |
| `revert` | 撤销既有提交 |

**判不准时按"对使用者的影响"选**：影响行为就是 `feat`/`fix`，只影响开发者就是 `chore`/`build`/`test`/`docs`。

## 3. scope 清单

写受影响的模块名，小写，用 `-` 连接：

```text
cli  list  add  copy  domain  fs  frontmatter  paths  i18n  config
```

新模块出现时再扩这张表，不要临时造 `misc`、`core`、`*` 这类含糊 scope。

## 4. 四条硬规则

1. **subject 用英文祈使句、小写开头**：`add` / `drop` / `wrap` / `stop`，不写 `added` / `Adds`。
2. **subject ≤ 72 字符**，细节挪正文。
3. **不加句号**。
4. **标题后空一行**，正文用中文写**为什么**，不写"改了什么"——diff 已经说明了。

```text
feat(list): wrap descriptions with hanging indent

终端宽度不够时描述会被硬切成多段，读起来像换了行。
按列宽贪心填充，续行缩进对齐，保持一段话是一个整体。
```

## 5. 正反对照

| 写法 | 判定 | 原因 |
|---|---|---|
| `feature(list): wrap x` | ✗ | 未知 type |
| `feat(list): Wrap x` | ✗ | 大写开头 |
| `feat(list): wrap x.` | ✗ | 句号结尾 |
| `feat(list): wrap x\nbody` | ✗ | 缺空行 |
| `feat: update code` | ✗ | subject 没有信息量 |
| `feat(list): wrap descriptions with hanging indent` | ✓ | |
| `feat(cli)!: drop the upgrade flag` | ✓ | 破坏性变更 |

## 6. 拆分原则

一个提交只表达一个意图，能单独 revert。

- TDD 循环拆成 `test:`（红）→ `feat:`（绿）→ `refactor:`，本仓库历史即如此。
- 文档与实现分开提交，避免 revert 实现时连带删掉文档。
- 重构与改行为**不要**混在一个提交里。
- 一次提交里出现两个不相关的目录，通常说明该拆。

## 7. 提交前自查

1. 首行能不看 diff 就说出改了什么吗？
2. type 是按"对使用者的影响"选的，还是按"改了哪些文件"选的？
3. scope 在清单里吗？
4. subject 有句号、大写开头、或超过 72 字符吗？
5. 标题和正文之间有空行吗？正文讲的是"为什么"吗？

## 8. 附录：需要硬约束时（可选）

本仓库默认**不装**钩子，靠这份约定加 review。若某个阶段需要强制，可自行加 `commit-msg` 钩子：

```sh
# .githooks/commit-msg，行尾必须是 LF
#!/bin/sh
subject=$(sed -n '1p' "$1" | tr -d '\r')
printf '%s\n' "$subject" | grep -Eq '^(feat|fix|docs|style|refactor|perf|test|build|ci|chore|revert)(\([a-z0-9._-]+\))?!?: .+' \
  || { printf '非 Conventional Commits：%s\n' "$subject" >&2; exit 1; }
```

```bash
git config core.hooksPath .githooks   # 每个克隆执行一次
```

注意三点：钩子文件必须是 LF 换行、文件名不带扩展名、`--no-verify` 能绕过，所以它只是"提醒"，真正的约束要靠 review。
