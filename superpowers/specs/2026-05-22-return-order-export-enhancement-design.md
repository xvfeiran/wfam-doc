# 退件列表导出增强设计

## 概述

增强现有退件列表 Excel 导出功能：
1. 导出内容从「退件单摘要」扩展为「退件单 + Parts 明细」平铺结构
2. 新增导出数量阈值控制，超过阈值拒绝并提示缩小筛选范围
3. 阈值通过 application.yml 可配置

## 方案选择

**方案 A：单 Sheet 平铺**（已选定）

每行一个 Part，退件单头信息在每行重复。适合数据分析、透视、筛选。

备选方案 B（双 Sheet）和方案 C（合并单元格）因用户体验或操作性问题被排除。

## 导出表结构（40 列）

每行一个 Part，退件单头字段平铺重复：

### 退件单字段（11 列）

| 列序号 | 列名 | 来源字段 | 类型 |
|--------|------|---------|------|
| 1 | 退货单号 | orderNumber | String |
| 2 | 客户 | customer | String |
| 3 | 收货日期 | receiveDate | LocalDate |
| 4 | 投诉日期 | complaintDate | LocalDate |
| 5 | 退货方式 | returnMethod | String (快递/自提) |
| 6 | 物流单号 | trackingNumber | String |
| 7 | 退货数量 | returnQuantity | Integer |
| 8 | 投诉类型 | complaintType | String |
| 9 | 退货单状态 | ReturnOrder.status | String (草稿/已提交/已报废) |
| 10 | 退货单创建人 | ReturnOrder.createdBy | String |
| 11 | 退货单创建时间 | ReturnOrder.createdAt | LocalDateTime |

### 零件信息字段（13 列）

| 列序号 | 列名 | 来源字段 | 类型 |
|--------|------|---------|------|
| 12 | 退件编号 | partNumber | String |
| 13 | 零件代码(FIS) | partCode | String |
| 14 | 事业群 | businessUnit | String |
| 15 | 产品平台 | productPlatform | String |
| 16 | 生产班次 | productionShift | String |
| 17 | 客户故障类型 | failureType | String (NVH/外观/功能) |
| 18 | 博世故障类型 | boschFailureType | String |
| 19 | 零件状态 | Part.status | String |
| 20 | 是否取样 | isSample | String (是/否) |
| 21 | QC编号 | qcNo | String |
| 22 | 责任工程师 | responsibleEngineer | String |
| 23 | 分析师 | analyst | String |
| 24 | 投诉位置 | complaintLocation | String |

### 车辆信息字段（5 列）

| 列序号 | 列名 | 来源字段 | 类型 |
|--------|------|---------|------|
| 25 | 车辆生产日期 | vehicleProductionDate | LocalDate |
| 26 | 车辆购买日期 | vehiclePurchaseDate | LocalDate |
| 27 | 车辆故障日期 | vehicleFailureDate | LocalDate |
| 28 | VIN | vehicleVin | String |
| 29 | 里程(km) | vehicleMileage | Integer |

### 描述字段（3 列）

| 列序号 | 列名 | 来源字段 | 类型 |
|--------|------|---------|------|
| 30 | 客户描述 | customerDescription | String |
| 31 | 其他描述 | otherDescription | String |
| 32 | 维修站 | repairStation | String |

### 审计字段（4 列）

| 列序号 | 列名 | 来源字段 | 类型 |
|--------|------|---------|------|
| 33 | 零件创建人 | Part.createdBy | String |
| 34 | 零件创建时间 | Part.createdAt | LocalDateTime |
| 35 | 零件更新人 | Part.updatedBy | String |
| 36 | 零件更新时间 | Part.updatedAt | LocalDateTime |

### 汇总字段（4 列）

| 列序号 | 列名 | 来源字段 | 类型 |
|--------|------|---------|------|
| 37 | 初始分析数量 | initialAnalysisQuantity | Integer |
| 38 | 精细分析数量 | detailedAnalysisQuantity | Integer |
| 39 | 报废数量 | scrappedQuantity | Integer |
| 40 | QC已创建数量 | qcCreatedQuantity | Integer |

### 值映射规则

| 原始值 | 导出显示 |
|--------|---------|
| returnMethod: "express" | 快递 |
| returnMethod: "pickup" | 自提 |
| ReturnOrder.status: "draft" | 草稿 |
| ReturnOrder.status: "submitted" | 已提交 |
| ReturnOrder.status: "scrapped" | 已报废 |
| isSample: 0 | 否 |
| isSample: 1 | 是 |
| failureType: "NVH" | NVH |
| failureType: "APPEARANCE" | 外观 |
| failureType: "FUNCTION" | 功能 |

## 阈值控制

### 配置

```yaml
# application.yml
aftermarket-parts:
  export:
    max-rows: 10000
```

默认值 10,000。通过 `@ConfigurationProperties` 绑定，启动时可改。

### 流程

```
用户点击导出
  → 前端传当前筛选条件（orderNumber, customer, status, receiveDateStart, receiveDateEnd）
  → 后端先用同样条件 COUNT(Parts)
    ├── COUNT ≤ maxRows → 查询 ReturnOrder + Parts → 平铺写入 Excel → 返回文件流
    └── COUNT > maxRows → 返回 HTTP 400 + { message: "导出数据量(X条)超过上限(Y条)，请缩小筛选条件范围" }
      → 前端 Modal.warning 展示提示
```

关键点：
- COUNT 查询与实际导出共用同一筛选条件
- 超量时返回具体数量，用户知道差多少
- 前端用 `Modal.warning` 而非 alert

## 技术实现

### 后端改动

1. **新增 `ExportProperties`**：`@ConfigurationProperties(prefix = "aftermarket-parts.export")`，持有 `maxRows` 字段，默认 10000
2. **改造 `ReturnOrderService.exportToExcel()`**：
   - 注入 `ExportProperties`
   - 先用筛选条件查询 Parts 的 COUNT
   - 超阈值时抛 `ExportLimitExceededException(actual, limit)`
   - 未超阈值则查询 ReturnOrder（含 Parts），平铺写入 Excel
3. **改造 `ReturnOrderExcelHandler`**：从 7 列扩展到 40 列，增加 Part 字段写入和值映射
4. **新增 `ExportLimitExceededException`**：携带实际数量和上限值
5. **改造 `GlobalExceptionHandler`**：捕获 `ExportLimitExceededException`，返回 HTTP 400 + 结构化错误信息

### 前端改动

1. **`returnOrderApi.ts`**：`exportExcel` 方法处理 400 响应，提取错误信息并抛出
2. **导出调用处**（`OrderListActions.vue` 或 `OrderList.vue`）：捕获导出异常，用 `Modal.warning` 展示超量提示

### 不改动的部分

- 导出按钮位置和交互不变
- 现有筛选逻辑不变
- API 路径 `GET /api/v1/return-orders/export` 不变
- 查询参数不变（orderNumber, customer, status, receiveDateStart, receiveDateEnd）

## Excel 文件格式

- 格式：`.xlsx`（Apache POI XSSFWorkbook）
- Sheet 名：`退件明细`
- 文件名：`退件明细_YYYYMMDD.xlsx`
- 列宽：自动适配（autoSize）
- 日期格式：`yyyy-MM-dd`
- 日期时间格式：`yyyy-MM-dd HH:mm:ss`
- 表头行：加粗，浅灰背景
