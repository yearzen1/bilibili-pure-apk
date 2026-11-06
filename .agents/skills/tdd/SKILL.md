---
name: tdd
description: "Use for all Agent-generated code changes to enforce strict test-driven development: Red → Green → Refactor, minimal implementation, full test verification, and atomic commits."
---

# TDD

## 1. 核心原则

本仓库遵循测试驱动开发（TDD）。所有由 Agent 生成的代码变更，必须严格遵循 TDD 循环：

```text
Red → Green → Refactor
```

- Red（红）：先写一个失败的测试，明确期望的行为。
- Green（绿）：用最少的代码让测试通过。
- Refactor（重构）：在测试保护下清理代码，不改变行为。

禁止先写实现再补测试。没有对应失败测试的实现代码，视为违规。

## 2. 工作流程（Agent 必须遵守）

### 步骤 0：理解需求

- 阅读任务描述，列出所有可验证的行为点。
- 将每个行为点映射为一个测试用例。
- 若需求模糊，先提问或写出假设，不要直接猜测实现。

### 步骤 1：Red — 写失败测试

- 在测试文件中新增测试，命名清晰描述行为，如 `should_return_zero_for_empty_cart`。
- 运行测试，确认它因预期原因失败，而不是语法错误或导入错误。
- 记录失败信息。

### 步骤 2：Green — 最小实现

- 只写让当前失败测试通过的最少代码。
- 不提前实现未被测试覆盖的功能（YAGNI）。
- 运行全部测试，确保新测试通过且旧测试不回归。

### 步骤 3：Refactor — 重构

- 在测试全绿的前提下，消除重复、改善命名、抽取函数。
- 每次重构后立即运行测试。
- 若重构引入新行为，回到步骤 1。

### 步骤 4：提交

- 每个 TDD 循环对应一个原子提交。
- 提交信息格式：`test: ...`、`feat: ...`、`refactor: ...`。
