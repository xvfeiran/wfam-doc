# 退货单阶段闸口设计

## 问题

分析单已报废后，仍能向同一退货单添加新零件，导致状态不一致。

## 方案

将退货单的 `submitted` 状态作为「收件截止」闸口，把「收退件」和「做分析」两个阶段分离。

### 状态模型

```
draft → submitted → scrapped
```

| 退货单状态 | 可加零件 | 分析单 | 说明 |
|---|---|---|---|
| `draft` | 可 | 不存在 | 收件阶段 |
| `submitted` | 否 | 已批量创建 | 分析阶段 |
| `scrapped` | 否 | 已完成 | 结束 |

### 核心变更

分析单的创建时机从"零件创建时"改为"退货单提交时"。

## 后端改动

### PartService.create()

- 只允许 `draft` 状态添加零件（原来是 `draft` 或 `submitted`）
- 移除 `getOrCreate()` 调用，零件创建时不再触发分析单创建

### ReturnOrderService.submit()

在原有逻辑（改状态 + 生成单号）基础上增加：

1. 校验退货单下必须有至少一个零件，否则拒绝提交
2. 按零件的 `analyst` 字段分组，为每个 analyst 创建一个分析单
   - 售后件（BA40/BA41）：初始状态 `pending_sampling`
   - 0km 件：初始状态 `analysis_completed`
3. 零件的初始状态保持不变（`in_initial_analysis` / `analysis_skipped`）

### 导入流程（不变）

- `createAndSubmitForImport()` 仍直接创建 `submitted` 退货单
- `createForImport()` 零件仍直接 `scrapped`
- 不创建分析单（导入是历史数据录入）

### 零件删除

零件删除只在 `draft` 状态允许。`draft` 下无分析单，级联逻辑不受影响。

## 前端改动

### 退货单详情页

- `draft` 状态：显示「结束录入」按钮，可添加/编辑/删除零件
- `submitted` 状态：隐藏添加/编辑/删除操作，显示分析单列表
- 「结束录入」按钮点击后：
  1. 校验至少有一个零件
  2. 弹出确认框：「确认结束录入？提交后将无法添加或删除零件，并自动创建分析单。」
  3. 确认后调用 submit 接口
  4. 页面刷新，切换到分析阶段视图

### 零件列表页

- `submitted` 状态下隐藏所有增删改操作，只显示只读视图

### 导入页面

不变。
