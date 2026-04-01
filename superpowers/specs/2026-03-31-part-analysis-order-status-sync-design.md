# 售后件与分析单状态联动设计文档

**日期**：2026-03-31
**状态**：已确认

---

## 1. 背景与目标

售后件（Part）和分析单（AnalysisOrder）当前状态独立维护，缺乏联动：
- `AnalysisReportService` 的 submit / approve / reject 不更新 Part 和 AnalysisOrder 状态
- `AnalysisOrderService` 的 scrap / workonConfirm 不更新关联 Part 状态

目标：确保 Part 状态与 AnalysisOrder 状态保持一致的关系。

---

## 2. 状态映射

### 2.1 Part 状态（新增 `pending_approval`）

| 状态值 | 含义 |
|---|---|
| `in_initial_analysis` | 初分析中 |
| `in_detailed_analysis` | 精分析中 |
| `pending_approval` | **新增** 精分析报告待审批 |
| `analysis_completed` | 分析完成 |
| `scrap_in_progress` | 报废中 |
| `scrapped` | 已报废 |

### 2.2 联动规则

| 触发事件 | 前置条件 | 分析单新状态 | 已抽样件新状态 | 未抽样件新状态 |
|---|---|---|---|---|
| 报告提交 | 所有抽样件报告均已提交 | `pending_approval` | `pending_approval` | 不变 |
| 报告提交 | 仍有抽样件未提交 | 不变 | `pending_approval` | 不变 |
| 报告审批通过 | 所有抽样件报告均已通过 | `analysis_completed` | `analysis_completed` | 不变 |
| 报告审批通过 | 仍有抽样件未通过 | 不变 | `analysis_completed` | 不变 |
| 报告审批驳回 | 分析单当前为 `pending_approval` | `in_detailed_analysis` | 被驳回件→`in_detailed_analysis` | 不变 |
| 报告审批驳回 | 分析单不是 `pending_approval` | 不变 | 被驳回件→`in_detailed_analysis` | 不变 |
| 报废申请 | - | `workon_scrap_in_progress` | `scrap_in_progress` | `scrap_in_progress` |
| WorkON确认 | - | `workon_scrapped` | `scrapped` | `scrapped` |

---

## 3. 实体关联

```
AnalysisOrder (orderId + analyst)
    └── Part (orderId + analyst) [多条，isSample=0/1]
            └── AnalysisReport (partId) [1:1]
```

查找 Part 所属 AnalysisOrder：`analysisOrderRepo.findByOrderIdAndAnalyst(part.orderId, part.analyst)`

---

## 4. 修改范围

### 4.1 AnalysisReportService

注入：`PartRepository`、`AnalysisOrderRepository`

**submit(reportId, submittedBy)**：
1. Report → `submitted`
2. Part → `pending_approval`
3. 找 AnalysisOrder；若所有抽样件均为 `pending_approval` → AnalysisOrder → `pending_approval`

**approve(reportId, approvedBy, comment)**：
1. Report → `approved`
2. Part → `analysis_completed` + `statusChangedAt`
3. 找 AnalysisOrder；若所有抽样件均为 `analysis_completed` → AnalysisOrder → `analysis_completed` + `statusChangedAt`

**reject(reportId, approvedBy, reason)**：
1. Report → `rejected`
2. Part → `in_detailed_analysis` + `statusChangedAt`
3. 找 AnalysisOrder；若当前为 `pending_approval` → 回退为 `in_detailed_analysis` + `statusChangedAt`

### 4.2 AnalysisOrderService

**scrap(id)**：
1. AnalysisOrder → `workon_scrap_in_progress` + `statusChangedAt`（现有）
2. **新增**：查所有关联 Part（orderId + analyst，不限 isSample）→ 全部 → `scrap_in_progress` + `statusChangedAt`

**workonConfirm(id)**：
1. AnalysisOrder → `workon_scrapped` + `statusChangedAt`（现有）
2. **新增**：查所有关联 Part → 全部 → `scrapped` + `statusChangedAt`

---

## 5. 不变范围

- Part 的 `updateStatus` API 仍保留（供后续扩展）
- `PartService.updateQcNo` 允许的状态集合需补充 `pending_approval`（`analysis_completed`、`scrap_in_progress`、`scrapped` 已有）
- 前端无需修改（状态值通过现有接口返回）

---

## 6. 文档同步

修改完成后需同步更新：
- `./doc/01-设计文档/开发设计文档.md`：2.2.3 售后件状态转换图 + 2.2.4 分析单联动说明
- `./doc/02-工作进度/测试文档.md`：新增相关测试用例
