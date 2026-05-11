# 拆分维修站号与投诉地字段实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 `repairStationLocation` 合并字段拆回 `repairStation` + `complaintLocation` 两个独立字段。

**Architecture:** 新增数据库迁移脚本 V38 拆分列，然后依次修改后端 Entity/DTO/Service/OCR/Import，最后修改前端类型/组件/i18n。所有变更保持 API 兼容（字段名从 `repairStationLocation` 变为 `repairStation` + `complaintLocation`）。

**Tech Stack:** Oracle SQL (Flyway), Java 21 / Spring Boot / JPA, Vue 3 / TypeScript / Ant Design Vue / i18n

---

### Task 1: 数据库迁移脚本

**Files:**
- Create: `backend/src/main/resources/db/migration/V38__split_repair_station_location.sql`

- [ ] **Step 1: 编写迁移脚本**

```sql
-- 拆分维修站号和投诉地字段（反向 V33 合并）
-- 1. 新增两个独立列
ALTER TABLE APMS_PART ADD REPAIR_STATION VARCHAR2(100 CHAR);
ALTER TABLE APMS_PART ADD COMPLAINT_LOCATION VARCHAR2(100 CHAR);

-- 2. 迁移数据：按首个 '-' 分隔拆入两列
UPDATE APMS_PART
SET REPAIR_STATION =
    CASE
        WHEN REPAIR_STATION_LOCATION IS NOT NULL
         AND INSTR(REPAIR_STATION_LOCATION, '-') > 0
        THEN SUBSTR(REPAIR_STATION_LOCATION, 1, INSTR(REPAIR_STATION_LOCATION, '-') - 1)
        ELSE REPAIR_STATION_LOCATION
    END,
    COMPLAINT_LOCATION =
    CASE
        WHEN REPAIR_STATION_LOCATION IS NOT NULL
         AND INSTR(REPAIR_STATION_LOCATION, '-') > 0
        THEN SUBSTR(REPAIR_STATION_LOCATION, INSTR(REPAIR_STATION_LOCATION, '-') + 1)
        ELSE NULL
    END;

-- 3. 删除合并列
ALTER TABLE APMS_PART DROP COLUMN REPAIR_STATION_LOCATION;
```

- [ ] **Step 2: Commit**

```bash
cd backend
git add src/main/resources/db/migration/V38__split_repair_station_location.sql
git commit -m "feat(db): split REPAIR_STATION_LOCATION into REPAIR_STATION and COMPLAINT_LOCATION"
```

---

### Task 2: 后端 Entity — Part.java

**Files:**
- Modify: `backend/src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/entity/Part.java:73-74`

- [ ] **Step 1: 替换字段**

将:
```java
    @Column(name = "REPAIR_STATION_LOCATION", length = 255)
    private String repairStationLocation;
```

替换为:
```java
    @Column(name = "REPAIR_STATION", length = 100)
    private String repairStation;

    @Column(name = "COMPLAINT_LOCATION", length = 100)
    private String complaintLocation;
```

- [ ] **Step 2: Commit**

```bash
cd backend
git add src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/entity/Part.java
git commit -m "refactor(entity): split repairStationLocation into repairStation + complaintLocation"
```

---

### Task 3: 后端 DTO — PartDTO.java

**Files:**
- Modify: `backend/src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/dto/PartDTO.java:35`

- [ ] **Step 1: 替换字段**

将:
```java
    private String repairStationLocation;
```

替换为:
```java
    private String repairStation;
    private String complaintLocation;
```

- [ ] **Step 2: Commit**

```bash
cd backend
git add src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/dto/PartDTO.java
git commit -m "refactor(dto): split repairStationLocation into repairStation + complaintLocation in PartDTO"
```

---

### Task 4: 后端 DTO — OcrResultDTO.java

**Files:**
- Modify: `backend/src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/dto/OcrResultDTO.java:37-38`

- [ ] **Step 1: 替换字段**

将:
```java
    @Schema(description = "维修站号/投诉地", example = "avatrzhz0102001-阿维塔中心 郑州郑东新区店")
    private String repairStationLocation;
```

替换为:
```java
    @Schema(description = "维修站号", example = "avatrzhz0102001")
    private String repairStation;

    @Schema(description = "投诉地", example = "阿维塔中心 郑州郑东新区店")
    private String complaintLocation;
```

- [ ] **Step 2: Commit**

```bash
cd backend
git add src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/dto/OcrResultDTO.java
git commit -m "refactor(dto): split repairStationLocation into repairStation + complaintLocation in OcrResultDTO"
```

---

### Task 5: 后端 Service — PartService.java

**Files:**
- Modify: `backend/src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/service/PartService.java` (lines 212, 273, 356, 461, 647)

- [ ] **Step 1: 替换所有引用**

在 PartService.java 中做以下替换（共 5 处）：

**Line 212 附近（create 方法 builder）：**
将 `.repairStationLocation(trimText(dto.getRepairStationLocation()))` 替换为：
```java
.repairStation(trimText(dto.getRepairStation()))
.complaintLocation(trimText(dto.getComplaintLocation()))
```

**Line 273 附近（import create builder）：**
将 `.repairStationLocation(trimText(dto.getRepairStationLocation()))` 替换为：
```java
.repairStation(trimText(dto.getRepairStation()))
.complaintLocation(trimText(dto.getComplaintLocation()))
```

**Line 356 附近（batch import builder）：**
将 `.repairStationLocation(trimText(dto.getRepairStationLocation()))` 替换为：
```java
.repairStation(trimText(dto.getRepairStation()))
.complaintLocation(trimText(dto.getComplaintLocation()))
```

**Line 461 附近（update 方法）：**
将 `part.setRepairStationLocation(trimText(dto.getRepairStationLocation()));` 替换为：
```java
part.setRepairStation(trimText(dto.getRepairStation()));
part.setComplaintLocation(trimText(dto.getComplaintLocation()));
```

**Line 647 附近（buildDTO 方法）：**
将 `.repairStationLocation(part.getRepairStationLocation())` 替换为：
```java
.repairStation(part.getRepairStation())
.complaintLocation(part.getComplaintLocation())
```

- [ ] **Step 2: Commit**

```bash
cd backend
git add src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/service/PartService.java
git commit -m "refactor(service): split repairStationLocation into repairStation + complaintLocation in PartService"
```

---

### Task 6: 后端 Service — ReturnOrderService.java

**Files:**
- Modify: `backend/src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/service/ReturnOrderService.java:539`

- [ ] **Step 1: 替换引用**

将 `.repairStationLocation(part.getRepairStationLocation())` 替换为：
```java
.repairStation(part.getRepairStation())
.complaintLocation(part.getComplaintLocation())
```

- [ ] **Step 2: Commit**

```bash
cd backend
git add src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/service/ReturnOrderService.java
git commit -m "refactor(service): split repairStationLocation into repairStation + complaintLocation in ReturnOrderService"
```

---

### Task 7: 后端 Service — OcrService.java

**Files:**
- Modify: `backend/src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/service/OcrService.java:231-233`

- [ ] **Step 1: 替换 applyOcrToPart 中的字段写入**

将:
```java
        if (result.getRepairStationLocation() != null) {
            part.setRepairStationLocation(result.getRepairStationLocation());
        }
```

替换为:
```java
        if (result.getRepairStation() != null) {
            part.setRepairStation(result.getRepairStation());
        }
        if (result.getComplaintLocation() != null) {
            part.setComplaintLocation(result.getComplaintLocation());
        }
```

- [ ] **Step 2: Commit**

```bash
cd backend
git add src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/service/OcrService.java
git commit -m "refactor(service): split repairStationLocation in OcrService.applyOcrToPart"
```

---

### Task 8: 后端 Service — OcrAsyncProcessor.java

**Files:**
- Modify: `backend/src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/service/OcrAsyncProcessor.java:247-248, 266, 276`

- [ ] **Step 1: 更新 mapChineseFields 方法的字段映射**

**Line 247-248 注释更新：**
将:
```
     * - repair_station: 维修站号/投诉地
```
替换为:
```
     * - repair_station: 维修站号
     * - complaint_location: 投诉地
```

**Line 266 附近 — 解析字段：**
将:
```java
        String repairStation  = textOrAlt(node, "repair_station", "维修站号/投诉地");
```
替换为:
```java
        String repairStation  = textOrAlt(node, "repair_station", "维修站号");
        String complaintLocation = textOrAlt(node, "complaint_location", "投诉地");
```

**Line 268 日志更新：**
将:
```java
log.debug("字段映射结果: productionDate={}, purchaseDate={}, failureDate={}, vin={}, mileage={}, description={}, repairStation={}",
                productionDate, purchaseDate, failureDate, vin, mileageStr, description, repairStation);
```
替换为:
```java
log.debug("字段映射结果: productionDate={}, purchaseDate={}, failureDate={}, vin={}, mileage={}, description={}, repairStation={}, complaintLocation={}",
                productionDate, purchaseDate, failureDate, vin, mileageStr, description, repairStation, complaintLocation);
```

**Line 276 附近 — builder 设置：**
将:
```java
        builder.repairStationLocation(repairStation);
```
替换为:
```java
        builder.repairStation(repairStation);
        builder.complaintLocation(complaintLocation);
```

- [ ] **Step 2: Commit**

```bash
cd backend
git add src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/service/OcrAsyncProcessor.java
git commit -m "refactor(ocr): split repair_station and complaint_location in OcrAsyncProcessor"
```

---

### Task 9: 后端 Import — PartImportParser.java

**Files:**
- Modify: `backend/src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/service/excel/PartImportParser.java:192, 270`

- [ ] **Step 1: 更新 parseRow 中的字段映射**

**Line 192 — builder 设置：**
将:
```java
                .repairStationLocation(normalizeText(getCellString(row, mapping.repairStationCol())))
```
替换为:
```java
                .repairStation(normalizeText(getCellString(row, mapping.repairStationCol())))
```

注意：`complaintLocation` 不从 Excel 读取，保持为 null。

**Line 270 — captureRawData：**
将:
```java
        raw.put("repairStationLocation", getCellString(row, mapping.repairStationCol()));
```
替换为:
```java
        raw.put("repairStation", getCellString(row, mapping.repairStationCol()));
```

- [ ] **Step 2: Commit**

```bash
cd backend
git add src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/service/excel/PartImportParser.java
git commit -m "refactor(import): map Excel 维修站号 to repairStation field"
```

---

### Task 10: 前端类型 — types/index.ts

**Files:**
- Modify: `frontend/src/types/index.ts:67`

- [ ] **Step 1: 替换 Part interface 字段**

将:
```typescript
  repairStationLocation?: string
```

替换为:
```typescript
  repairStation?: string
  complaintLocation?: string
```

- [ ] **Step 2: Commit**

```bash
cd frontend
git add src/types/index.ts
git commit -m "refactor(types): split repairStationLocation into repairStation + complaintLocation"
```

---

### Task 11: 前端 OCR API — ocrApi.ts

**Files:**
- Modify: `frontend/src/services/ocrApi.ts:10`

- [ ] **Step 1: 替换 OcrResult interface 字段**

将:
```typescript
  repairStationLocation?: string
```

替换为:
```typescript
  repairStation?: string
  complaintLocation?: string
```

- [ ] **Step 2: Commit**

```bash
cd frontend
git add src/services/ocrApi.ts
git commit -m "refactor(ocr-api): split repairStationLocation in OcrResult interface"
```

---

### Task 12: 前端 Composable — useOCR.ts

**Files:**
- Modify: `frontend/src/composables/useOCR.ts:22, 208`

- [ ] **Step 1: 更新 OCR_FIELDS 常量**

将 `OCR_FIELDS` 数组中的 `'repairStationLocation'` 替换为两个新字段：

```typescript
const OCR_FIELDS = [
  'vehicleProductionDate',
  'vehiclePurchaseDate',
  'vehicleFailureDate',
  'vehicleVIN',
  'vehicleMileage',
  'customerDescription',
  'repairStation',
  'complaintLocation',
] as const
```

- [ ] **Step 2: 更新 writeResultsToForm 中的 map**

将:
```typescript
      repairStationLocation: result?.repairStationLocation,
```

替换为:
```typescript
      repairStation: result?.repairStation,
      complaintLocation: result?.complaintLocation,
```

- [ ] **Step 3: Commit**

```bash
cd frontend
git add src/composables/useOCR.ts
git commit -m "refactor(ocr): split repairStationLocation in useOCR composable"
```

---

### Task 13: 前端 i18n — zh-CN.ts

**Files:**
- Modify: `frontend/src/i18n/locales/zh-CN.ts` (lines 119, 589)

- [ ] **Step 1: 替换两处翻译 key**

**Line 119（returnPart section）：**
将 `repairStationLocation: '维修站号/投诉地',` 替换为:
```typescript
    repairStation: '维修站号',
    complaintLocation: '投诉地',
```

**Line 589（partDetail section）：**
将 `repairStationLocation: '维修站号/投诉地',` 替换为:
```typescript
    repairStation: '维修站号',
    complaintLocation: '投诉地',
```

- [ ] **Step 2: Commit**

```bash
cd frontend
git add src/i18n/locales/zh-CN.ts
git commit -m "refactor(i18n): split repairStationLocation into repairStation + complaintLocation (zh-CN)"
```

---

### Task 14: 前端 i18n — en-US.ts

**Files:**
- Modify: `frontend/src/i18n/locales/en-US.ts` (lines 113, 572)

- [ ] **Step 1: 替换两处翻译 key**

**Line 113（returnPart section）：**
将 `repairStationLocation: 'Service Station/Location',` 替换为:
```typescript
    repairStation: 'Service Station',
    complaintLocation: 'Complaint Location',
```

**Line 572（partDetail section）：**
将 `repairStationLocation: 'Service Station/Location',` 替换为:
```typescript
    repairStation: 'Service Station',
    complaintLocation: 'Complaint Location',
```

- [ ] **Step 2: Commit**

```bash
cd frontend
git add src/i18n/locales/en-US.ts
git commit -m "refactor(i18n): split repairStationLocation into repairStation + complaintLocation (en-US)"
```

---

### Task 15: 前端组件 — ComplaintInfoCard.vue

**Files:**
- Modify: `frontend/src/views/return-parts/components/ComplaintInfoCard.vue`

- [ ] **Step 1: 替换模板中单个输入框为左右并排两个输入框**

将 template 中的第一组 `<a-row>` (lines 8-21):
```html
      <a-row :gutter="24">
        <a-col :span="12">
          <a-form-item
            :label="t('returnPart.repairStationLocation')"
            name="repairStationLocation"
          >
            <a-input
              v-model:value="form.repairStationLocation"
              :placeholder="t('returnPart.repairStationLocation')"
              allow-clear
            />
          </a-form-item>
        </a-col>
      </a-row>
```

替换为:
```html
      <a-row :gutter="24">
        <a-col :span="12">
          <a-form-item
            :label="t('returnPart.repairStation')"
            name="repairStation"
          >
            <a-input
              v-model:value="form.repairStation"
              :placeholder="t('returnPart.repairStation')"
              allow-clear
            />
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item
            :label="t('returnPart.complaintLocation')"
            name="complaintLocation"
          >
            <a-input
              v-model:value="form.complaintLocation"
              :placeholder="t('returnPart.complaintLocation')"
              allow-clear
            />
          </a-form-item>
        </a-col>
      </a-row>
```

- [ ] **Step 2: 更新 Form interface**

将 script 中的 Form interface (line 93):
```typescript
  repairStationLocation: string
```
替换为:
```typescript
  repairStation: string
  complaintLocation: string
```

- [ ] **Step 3: Commit**

```bash
cd frontend
git add src/views/return-parts/components/ComplaintInfoCard.vue
git commit -m "refactor(ComplaintInfoCard): split into repairStation + complaintLocation side-by-side"
```

---

### Task 16: 前端组件 — PartForm.vue

**Files:**
- Modify: `frontend/src/views/return-parts/PartForm.vue` (lines 143, 192, 243-245, 281)

- [ ] **Step 1: 更新 form reactive 对象**

将 form 中的 `repairStationLocation: '',` (line 143) 替换为:
```typescript
  repairStation: '',
  complaintLocation: '',
```

- [ ] **Step 2: 更新 populateForm 方法**

将 `form.repairStationLocation = part.repairStationLocation || ''` (line 192) 替换为:
```typescript
  form.repairStation = part.repairStation || ''
  form.complaintLocation = part.complaintLocation || ''
```

- [ ] **Step 3: 更新 handlePreviewConfirm 中的字段同步**

将:
```typescript
  if (previewForm.repairStationLocation !== undefined) {
    form.repairStationLocation = previewForm.repairStationLocation
  }
```
替换为:
```typescript
  if (previewForm.repairStation !== undefined) {
    form.repairStation = previewForm.repairStation
  }
  if (previewForm.complaintLocation !== undefined) {
    form.complaintLocation = previewForm.complaintLocation
  }
```

- [ ] **Step 4: 更新 buildPartPayload 方法**

将 `repairStationLocation: form.repairStationLocation || undefined,` (line 281) 替换为:
```typescript
  repairStation: form.repairStation || undefined,
  complaintLocation: form.complaintLocation || undefined,
```

- [ ] **Step 5: Commit**

```bash
cd frontend
git add src/views/return-parts/PartForm.vue
git commit -m "refactor(PartForm): split repairStationLocation into repairStation + complaintLocation"
```

---

### Task 17: 前端组件 — PartDetail.vue

**Files:**
- Modify: `frontend/src/views/return-parts/PartDetail.vue:65-67`

- [ ] **Step 1: 替换详情展示**

将:
```html
            <a-descriptions-item :label="t('returnPart.repairStationLocation')">
              {{ part?.repairStationLocation || '-' }}
            </a-descriptions-item>
```

替换为:
```html
            <a-descriptions-item :label="t('returnPart.repairStation')">
              {{ part?.repairStation || '-' }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('returnPart.complaintLocation')">
              {{ part?.complaintLocation || '-' }}
            </a-descriptions-item>
```

- [ ] **Step 2: Commit**

```bash
cd frontend
git add src/views/return-parts/PartDetail.vue
git commit -m "refactor(PartDetail): split repairStationLocation display into two fields"
```

---

### Task 18: 前端组件 — OCRPreviewModal.vue

**Files:**
- Modify: `frontend/src/views/return-parts/components/OCRPreviewModal.vue` (lines 107-112, 181, 189, 212-214)

- [ ] **Step 1: 替换模板中表单项**

将:
```html
          <a-form-item :label="t('returnPart.repairStationLocation')">
            <a-input
              v-model:value="localForm.repairStationLocation"
              :placeholder="t('validation.pleaseInput')"
            />
          </a-form-item>
```

替换为:
```html
          <a-form-item :label="t('returnPart.repairStation')">
            <a-input
              v-model:value="localForm.repairStation"
              :placeholder="t('validation.pleaseInput')"
            />
          </a-form-item>
          <a-form-item :label="t('returnPart.complaintLocation')">
            <a-input
              v-model:value="localForm.complaintLocation"
              :placeholder="t('validation.pleaseInput')"
            />
          </a-form-item>
```

- [ ] **Step 2: 更新 localForm reactive 定义**

将:
```typescript
  repairStationLocation: '',
```
替换为:
```typescript
  repairStation: '',
  complaintLocation: '',
```

同时更新 localForm 的类型声明，将 `repairStationLocation: string` 替换为:
```typescript
  repairStation: string
  complaintLocation: string
```

- [ ] **Step 3: 更新 watch 中的 OCR 结果映射**

将:
```typescript
    if (results.repairStationLocation?.status === 'success') {
      localForm.repairStationLocation = results.repairStationLocation.value
    }
```

替换为:
```typescript
    if (results.repairStation?.status === 'success') {
      localForm.repairStation = results.repairStation.value
    }
    if (results.complaintLocation?.status === 'success') {
      localForm.complaintLocation = results.complaintLocation.value
    }
```

- [ ] **Step 4: Commit**

```bash
cd frontend
git add src/views/return-parts/components/OCRPreviewModal.vue
git commit -m "refactor(OCRPreviewModal): split repairStationLocation into two fields"
```

---

### Task 19: 后端编译验证

- [ ] **Step 1: 编译后端确认无报错**

```bash
cd backend && C:/Users/XEF1CNG/.m2/wrapper/dists/apache-maven-3.9.12-bin/5nmfsn99br87k5d4ajlekdq10k/apache-maven-3.9.12/bin/mvn compile -q
```

Expected: `BUILD SUCCESS`

如果编译失败，根据错误信息检查是否有遗漏的 `repairStationLocation` 引用并修复。

---

### Task 20: 前端编译验证

- [ ] **Step 1: 编译前端确认无报错**

```bash
cd frontend && npx vue-tsc --noEmit
```

Expected: 无 TypeScript 错误

如果有类型错误，根据提示检查是否有遗漏的 `repairStationLocation` 引用并修复。

- [ ] **Step 2: Commit any fixes**

```bash
git add -A && git commit -m "fix: resolve remaining repairStationLocation references"
```
