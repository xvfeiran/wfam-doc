# 合并维修站号与投诉地字段实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 `repairStation` 和 `complaintLocation` 两个字段合并为 `repairStationLocation`，支持 OCR 直接写入

**Architecture:**
1. 数据库层：新增 `REPAIR_STATION_LOCATION` 列，迁移现有数据，删除旧列
2. 后端层：Entity/DTO 合并字段，OCR 解析添加新字段支持
3. 前端层：类型定义、i18n、UI 组件合并为单字段

**Tech Stack:**
- 后端: Java 21, Spring Boot, JPA, Flyway
- 前端: Vue 3, TypeScript, Ant Design Vue, i18n
- 数据库: H2 (开发/测试)

---

## Task 1: 数据库迁移脚本（Flyway）

**Files:**
- Create: `backend/src/main/resources/db/migration/V18__merge_repair_station_and_location.sql`

- [ ] **Step 1: 创建 Flyway 迁移脚本**

新建文件 `backend/src/main/resources/db/migration/V18__merge_repair_station_and_location.sql`:

```sql
-- 合并维修站号和投诉地字段
-- 1. 添加新列
ALTER TABLE APMS_PART ADD REPAIR_STATION_LOCATION VARCHAR(255);

-- 2. 迁移数据：将旧数据合并为新格式（站号-地点）
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

-- 3. 删除旧列
ALTER TABLE APMS_PART DROP COLUMN REPAIR_STATION;
ALTER TABLE APMS_PART DROP COLUMN COMPLAINT_LOCATION;
```

- [ ] **Step 2: 验证脚本语法**

确认 SQL 语法正确，H2 数据库支持 `||` 字符串连接操作符。

- [ ] **Step 3: 提交迁移脚本**

```bash
cd backend
git add src/main/resources/db/migration/V18__merge_repair_station_and_location.sql
git commit -m "feat(db): merge repairStation and complaintLocation into repairStationLocation"
```

---

## Task 2: 后端 Entity 字段合并

**Files:**
- Modify: `backend/src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/entity/Part.java`

- [ ] **Step 1: 修改 Part.java Entity**

定位文件中的这两个字段（约第 73-77 行），删除并替换：

```java
// 删除以下两行
@Column(name = "REPAIR_STATION", length = 100)
private String repairStation;

@Column(name = "COMPLAINT_LOCATION", length = 100)
private String complaintLocation;

// 替换为
@Column(name = "REPAIR_STATION_LOCATION", length = 255)
private String repairStationLocation;
```

- [ ] **Step 2: 提交 Entity 变更**

```bash
cd backend
git add src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/entity/Part.java
git commit -m "refactor(entity): merge repairStation and complaintLocation into repairStationLocation"
```

---

## Task 3: 后端 PartDTO 字段合并

**Files:**
- Modify: `backend/src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/dto/PartDTO.java`

- [ ] **Step 1: 修改 PartDTO.java**

定位文件中的这两个字段（约第 35-36 行），删除并替换：

```java
// 删除以下两行
private String repairStation;
private String complaintLocation;

// 替换为
@Schema(description = "维修站号/投诉地", example = "avatrzhz0102001-阿维塔中心 郑州郑东新区店")
private String repairStationLocation;
```

- [ ] **Step 2: 提交 DTO 变更**

```bash
cd backend
git add src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/dto/PartDTO.java
git commit -m "refactor(dto): merge repairStation and complaintLocation into repairStationLocation"
```

---

## Task 4: 后端 OcrResultDTO 添加字段

**Files:**
- Modify: `backend/src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/dto/OcrResultDTO.java`

- [ ] **Step 1: 在 OcrResultDTO.java 中添加新字段**

在 `customerDescription` 字段后添加：

```java
@Schema(description = "维修站号/投诉地", example = "avatrzhz0102001-阿维塔中心 郑州郑东新区店")
private String repairStationLocation;
```

完整修改后的类结构（在 `customerDescription` 字段后）：

```java
@Schema(description = "客户失效描述", example = "发动机异响，怠速不稳")
private String customerDescription;

@Schema(description = "维修站号/投诉地", example = "avatrzhz0102001-阿维塔中心 郑州郑东新区店")
private String repairStationLocation;
```

- [ ] **Step 2: 提交 OCR DTO 变更**

```bash
cd backend
git add src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/dto/OcrResultDTO.java
git commit -m "feat(ocr): add repairStationLocation field to OcrResultDTO"
```

---

## Task 5: 后端 OcrService 更新解析逻辑

**Files:**
- Modify: `backend/src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/service/OcrService.java`

- [ ] **Step 1: 在 applyOcrToPart 方法中添加新字段赋值**

定位 `applyOcrToPart` 方法（约第 196 行），在 `setCustomerDescription` 后添加：

```java
if (result.getCustomerDescription() != null) {
    part.setCustomerDescription(result.getCustomerDescription());
}
if (result.getRepairStationLocation() != null) {
    part.setRepairStationLocation(result.getRepairStationLocation());
}
```

- [ ] **Step 2: 提交 OCR Service 变更**

```bash
cd backend
git add src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/service/OcrService.java
git commit -m "feat(ocr): add repairStationLocation parsing in applyOcrToPart"
```

---

## Task 6: 前端 TypeScript 类型定义更新

**Files:**
- Modify: `frontend/src/types/index.ts`

- [ ] **Step 1: 修改 Part 接口**

定位 `Part` 接口（约第 49 行），删除并替换：

```typescript
// 删除以下两行
repairStation?: string
complaintLocation?: string

// 替换为
repairStationLocation?: string
```

- [ ] **Step 2: 提交类型定义变更**

```bash
cd frontend
git add src/types/index.ts
git commit -m "refactor(types): merge repairStation and complaintLocation into repairStationLocation"
```

---

## Task 7: 前端中文 i18n 更新

**Files:**
- Modify: `frontend/src/i18n/locales/zh-CN.ts`

- [ ] **Step 1: 修改中文翻译**

定位 `returnPart` 对象中的这两个键（约第 575-576 行），删除并替换：

```typescript
// 删除以下两行
repairStation: '维修站号',
complaintLocation: '投诉地',

// 替换为
repairStationLocation: '维修站号/投诉地',
```

- [ ] **Step 2: 提交中文 i18n 变更**

```bash
cd frontend
git add src/i18n/locales/zh-CN.ts
git commit -m "refactor(i18n): merge repairStation and complaintLocation translation keys"
```

---

## Task 8: 前端英文 i18n 更新

**Files:**
- Modify: `frontend/src/i18n/locales/en-US.ts`

- [ ] **Step 1: 修改英文翻译**

定位 `returnPart` 对象中的这两个键（约第 564-565 行），删除并替换：

```typescript
// 删除以下两行
repairStation: 'Repair Station',
complaintLocation: 'Complaint Location',

// 替换为
repairStationLocation: 'Service Station/Location',
```

- [ ] **Step 2: 提交英文 i18n 变更**

```bash
cd frontend
git add src/i18n/locales/en-US.ts
git commit -m "refactor(i18n): merge repairStation and complaintLocation translation keys (en)"
```

---

## Task 9: 前端 PartForm.vue 组件更新

**Files:**
- Modify: `frontend/src/views/return-parts/PartForm.vue`

- [ ] **Step 1: 修改 form reactive 对象**

定位 `form` 对象定义（约第 118 行），删除并替换：

```typescript
// 删除以下两行
repairStation: '',
complaintLocation: '',

// 替换为
repairStationLocation: '',
```

- [ ] **Step 2: 提交 PartForm 组件变更**

```bash
cd frontend
git add src/views/return-parts/PartForm.vue
git commit -m "refactor(PartForm): merge repairStation and complaintLocation form fields"
```

---

## Task 10: 前端 ComplaintInfoCard 组件更新

**Files:**
- Modify: `frontend/src/views/return-parts/components/ComplaintInfoCard.vue`

- [ ] **Step 1: 读取组件内容确定具体修改位置**

```bash
cd frontend
grep -n "repairStation\|complaintLocation" src/views/return-parts/components/ComplaintInfoCard.vue
```

- [ ] **Step 2: 修改模板部分 - 合并两个输入框为一个**

根据 grep 结果，将两个独立的 `a-form-item` 合并为一个：

```vue
<!-- 替换原有的两个 a-form-item 为 -->
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
```

- [ ] **Step 3: 提交 ComplaintInfoCard 组件变更**

```bash
cd frontend
git add src/views/return-parts/components/ComplaintInfoCard.vue
git commit -m "refactor(ComplaintInfoCard): merge repairStation and complaintLocation into single input"
```

---

## Task 11: 前端 PartDetail 组件更新

**Files:**
- Modify: `frontend/src/views/return-parts/PartDetail.vue`

- [ ] **Step 1: 读取组件内容确定具体修改位置**

```bash
cd frontend
grep -n "repairStation\|complaintLocation" src/views/return-parts/PartDetail.vue
```

- [ ] **Step 2: 根据搜索结果修改显示部分**

将两个独立的描述项合并为一个：

```vue
<!-- 替换原有的两个 a-descriptions-item 为 -->
<a-descriptions-item :label="t('returnPart.repairStationLocation')">
  {{ part.repairStationLocation || '-' }}
</a-descriptions-item>
```

- [ ] **Step 3: 提交 PartDetail 组件变更**

```bash
cd frontend
git add src/views/return-parts/PartDetail.vue
git commit -m "refactor(PartDetail): merge repairStation and complaintLocation display"
```

---

## Task 12: 后端 PartImportParser 更新（如有 Excel 导入功能）

**Files:**
- Modify: `backend/src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/service/excel/PartImportParser.java`

- [ ] **Step 1: 检查是否需要更新导入逻辑**

```bash
cd backend
grep -n "repairStation\|complaintLocation" src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/service/excel/PartImportParser.java
```

- [ ] **Step 2: 根据搜索结果更新字段映射**

如果搜索到结果，更新解析逻辑以使用 `repairStationLocation`。

- [ ] **Step 3: 提交导入解析器变更（如有修改）**

```bash
cd backend
git add src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/service/excel/PartImportParser.java
git commit -m "refactor(import): update repairStationLocation field mapping"
```

---

## Task 13: 端到端测试验证

- [ ] **Step 1: 启动后端服务**

```bash
cd backend
mvn clean install
mvn spring-boot:run
```

验证 Flyway 迁移脚本执行成功，无错误日志。

- [ ] **Step 2: 启动前端服务**

```bash
cd frontend
npm run dev
```

访问 http://localhost:5173

- [ ] **Step 3: 验证新建售后件功能**

1. 导航到「售后件管理」
2. 点击「新建售后件」
3. 填写表单，在「维修站号/投诉地」字段输入 `test001-测试维修站`
4. 点击「暂存」
5. 验证保存成功

- [ ] **Step 4: 验证编辑售后件功能**

1. 打开刚创建的售后件详情
2. 验证「维修站号/投诉地」字段正确显示为 `test001-测试维修站`
3. 修改字段值并保存
4. 验证更新成功

- [ ] **Step 5: 验证 OCR 识别功能**

1. 上传包含「维修站号/投诉地」字段的测试图片
2. 等待 OCR 识别完成
3. 验证识别结果正确写入「维修站号/投诉地」字段

- [ ] **Step 6: 验证数据库数据**

```bash
cd backend
# 使用 H2 控制台或连接数据库查询
SELECT REPAIR_STATION_LOCATION FROM APMS_PART WHERE PART_NUMBER LIKE 'WS-%';
```

验证数据正确存储在 `REPAIR_STATION_LOCATION` 列中。

---

## Task 14: 文档更新

**Files:**
- Modify: `doc/01-设计文档/开发设计文档.md`
- Modify: `doc/02-工作进度/测试文档.md`

- [ ] **Step 1: 更新开发设计文档**

在相关章节中更新字段说明，将 `repairStation` 和 `complaintLocation` 合并为 `repairStationLocation`。

- [ ] **Step 2: 更新测试文档**

更新测试用例中涉及这两个字段的测试步骤。

- [ ] **Step 3: 提交文档更新**

```bash
cd doc
git add 01-设计文档/开发设计文档.md 02-工作进度/测试文档.md
git commit -m "docs: update field descriptions after merging repairStation fields"
```

---

## 验收标准

完成所有任务后，系统应满足：

1. ✅ 数据库 `APMS_PART` 表只有 `REPAIR_STATION_LOCATION` 列，无 `REPAIR_STATION` 和 `COMPLAINT_LOCATION` 列
2. ✅ 后端 Entity/DTO/OCR 相关类只包含 `repairStationLocation` 字段
3. ✅ 前端类型定义和 UI 组件只使用 `repairStationLocation` 字段
4. ✅ OCR 识别结果可直接写入 `repairStationLocation` 字段
5. ✅ 现有数据正确迁移到新字段（格式：站号-地点 或 单独值）
6. ✅ 新建/编辑/查看售后件功能正常工作
