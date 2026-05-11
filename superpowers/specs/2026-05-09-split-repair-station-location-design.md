# 拆分维修站号与投诉地字段设计文档

**Date:** 2026-05-09
**Status:** Approved
**Reverts:** V33 合并迁移（2026-04-20）

## 背景

V33 迁移将 `REPAIR_STATION` 和 `COMPLAINT_LOCATION` 合并为 `REPAIR_STATION_LOCATION`。业务需求变回两个字段独立存储。

## 数据库变更

新迁移脚本 V50：
1. 新增 `REPAIR_STATION VARCHAR2(100 CHAR)` 和 `COMPLAINT_LOCATION VARCHAR2(100 CHAR)`
2. 按首个 `-` 分隔 `REPAIR_STATION_LOCATION` 数据拆入两列
3. 删除 `REPAIR_STATION_LOCATION` 列

## 后端改动

| 文件 | 变更 |
|------|------|
| `Part.java` | `repairStationLocation` → `repairStation` + `complaintLocation` |
| `PartDTO.java` | 同上 |
| `OcrResultDTO.java` | 同上 |
| `OcrAsyncProcessor.java` | 映射 `repair_station` / `complaint_location` 两个 OCR key |
| `PartImportParser.java` | 只映射"维修站号"→ `repairStation`，不读投诉地 |
| `PartService.java` | 对应字段名调整 |
| `ReturnOrderService.java` | 对应字段名调整 |
| `OcrService.java` | writeOcrResultToPart 映射调整 |

## 前端改动

| 文件 | 变更 |
|------|------|
| `types/index.ts` | `repairStationLocation` → `repairStation` + `complaintLocation` |
| `ComplaintInfoCard.vue` | 左右并排两个输入框 |
| `PartForm.vue` | 表单字段拆分 |
| `PartDetail.vue` | 详情展示拆分 |
| `OCRPreviewModal.vue` | OCR 预览字段拆分 |
| `useOCR.ts` | OCR 字段映射调整 |
| `ocrApi.ts` | OcrResult 接口调整 |
| `zh-CN.ts` / `en-US.ts` | 新增独立翻译 key |

## OCR Dify Workflow 改动（由同事负责）

输出变量拆为两个：
- `repair_station` → 维修站号（如 `avatrzhz0102001`）
- `complaint_location` → 投诉地（如 `阿维塔中心 郑州郑东新区店`）

后端 `OcrAsyncProcessor.mapChineseFields()` 同时兼容英文 key 和中文 key：
- `repair_station` / `维修站号` → `repairStation`
- `complaint_location` / `投诉地` → `complaintLocation`

## 导入行为

`PartImportParser` 只读取 Excel 中"维修站号"列写入 `repairStation`，`complaintLocation` 留空。

## 文档更新

- `doc/01-设计文档/开发设计文档.md`：字段说明更新
- `doc/03-API文档/售后数据识别.yml`：OCR 输出字段更新
