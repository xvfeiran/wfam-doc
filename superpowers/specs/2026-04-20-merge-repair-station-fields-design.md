# 合并维修站号与投诉地字段设计文档

**日期**: 2026-04-20
**作者**: Claude
**状态**: 已批准

## 1. 概述

将系统中现有的 `repairStation`（维修站号）和 `complaintLocation`（投诉地）两个独立字段合并为单一字段 `repairStationLocation`（维修站号/投诉地）。合并后的字段将存储完整格式如 `"avatrzhz0102001-阿维塔中心 郑州郑东新区店"` 的值，与 OCR 识别输出保持一致。

## 2. 背景与动机

- **当前问题**: OCR 识别输出为 `"维修站号/投诉地": "avatrzhz0102001-阿维塔中心 郑州郑东新区店"` 格式，但系统将其拆分为两个字段存储，导致 OCR 结果无法直接写入系统
- **业务需求**: 简化数据模型，使 OCR 识别结果能直接写入系统，减少字段转换逻辑

## 3. 变更范围

| 层级 | 变更内容 |
|------|----------|
| 数据库 | 新建 `REPAIR_STATION_LOCATION` 列，删除 `REPAIR_STATION` 和 `COMPLAINT_LOCATION` |
| 后端 Entity | `Part.java` 字段合并 |
| 后端 DTO | `PartDTO.java` 字段合并 |
| 后端 OCR | `OcrResultDTO.java` 添加字段，`OcrService.java` 更新解析逻辑 |
| 前端类型 | `types/index.ts` 的 `Part` 接口 |
| 前端组件 | `PartForm.vue`、`ComplaintInfoCard.vue` 等 |
| 前端 i18n | `zh-CN.ts`、`en-US.ts` 翻译键 |

## 4. 数据库变更

### 4.1 迁移脚本 (Flyway)

```sql
-- 新增列
ALTER TABLE APMS_PART ADD REPAIR_STATION_LOCATION VARCHAR(255);

-- 数据迁移：将旧数据合并为新格式
UPDATE APMS_PART
SET REPAIR_STATION_LOCATION =
    CASE
        WHEN REPAIR_STATION IS NOT NULL AND COMPLAINT_LOCATION IS NOT NULL
        THEN REPAIR_STATION || '-' || COMPLAINT_LOCATION
        WHEN REPAIR_STATION IS NOT NULL
        THEN REPAIR_STATION
        WHEN COMPLAINT_LOCATION IS NOT NULL
        THEN COMPLAINT_LOCATION
        ELSE NULL
    END;

-- 删除旧列
ALTER TABLE APMS_PART DROP COLUMN REPAIR_STATION;
ALTER TABLE APMS_PART DROP COLUMN COMPLAINT_LOCATION;
```

### 4.2 回滚脚本 (如需要)

```sql
-- 添加回旧列
ALTER TABLE APMS_PART ADD REPAIR_STATION VARCHAR(100);
ALTER TABLE APMS_PART ADD COMPLAINT_LOCATION VARCHAR(100);

-- 尝试拆分数据（简化处理，将整个值放入 REPAIR_STATION）
UPDATE APMS_PART
SET REPAIR_STATION = REPAIR_STATION_LOCATION
WHERE REPAIR_STATION_LOCATION IS NOT NULL;

-- 删除新列
ALTER TABLE APMS_PART DROP COLUMN REPAIR_STATION_LOCATION;
```

## 5. 后端变更

### 5.1 Entity 变更

**文件**: `backend/.../entity/Part.java`

```java
// 删除
@Column(name = "REPAIR_STATION", length = 100)
private String repairStation;

@Column(name = "COMPLAINT_LOCATION", length = 100)
private String complaintLocation;

// 新增
@Column(name = "REPAIR_STATION_LOCATION", length = 255)
private String repairStationLocation;
```

### 5.2 DTO 变更

**文件**: `backend/.../dto/PartDTO.java`

```java
// 删除
private String repairStation;
private String complaintLocation;

// 新增
@Schema(description = "维修站号/投诉地", example = "avatrzhz0102001-阿维塔中心 郑州郑东新区店")
private String repairStationLocation;
```

### 5.3 OCR DTO 变更

**文件**: `backend/.../dto/OcrResultDTO.java`

```java
// 新增
@Schema(description = "维修站号/投诉地", example = "avatrzhz0102001-阿维塔中心 郑州郑东新区店")
private String repairStationLocation;
```

### 5.4 OCR Service 变更

**文件**: `backend/.../service/OcrService.java`

在 `applyOcrToPart()` 方法中添加：

```java
if (result.getRepairStationLocation() != null) {
    part.setRepairStationLocation(result.getRepairStationLocation());
}
```

## 6. 前端变更

### 6.1 类型定义

**文件**: `frontend/src/types/index.ts`

```typescript
export interface Part {
  // 删除
  // repairStation?: string
  // complaintLocation?: string

  // 新增
  repairStationLocation?: string
  // ...
}
```

### 6.2 国际化

**文件**: `frontend/src/i18n/locales/zh-CN.ts`

```typescript
// 更新翻译键
'repairStationLocation': '维修站号/投诉地'
```

**文件**: `frontend/src/i18n/locales/en-US.ts`

```typescript
'repairStationLocation': 'Service Station/Location'
```

### 6.3 UI 组件

**文件**: `frontend/src/views/return-parts/PartForm.vue`
**文件**: `frontend/src/views/return-parts/components/ComplaintInfoCard.vue`

将两个输入框合并为一个，使用 `repairStationLocation` 字段。

## 7. 测试要点

1. **数据迁移**: 验证现有数据正确迁移到新字段
2. **OCR 识别**: 验证 OCR 结果能正确写入合并后的字段
3. **前端表单**: 验证单字段能正常输入和保存
4. **详情展示**: 验证合并后的字段正确显示

## 8. 实施顺序

1. 后端 Entity/DTO 变更
2. 数据库迁移脚本
3. OCR 逻辑更新
4. 前端类型和组件更新
5. 端到端测试
