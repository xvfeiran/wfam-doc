# 精分析模板占位符生成器 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在模板管理页加一个纯前端弹窗，让用户点选+输入即可生成 `[[type:fieldName:labelZh:labelEn:required:options]]` 占位符并复制到 Excel。

**Architecture:** 新增一个自包含的 `PlaceholderGeneratorModal.vue`，由 `TemplateManagement.vue` 持有可见性状态并渲染。不触碰 `Settings.vue`、后端、上传/解析/导出管线。

**Tech Stack:** Vue 3 (script setup) + Ant Design Vue 4 + TypeScript + vue-i18n。

## Global Constraints

- 占位符语法严格匹配后端正则 `\[\[([a-z]+):([^:]+):([^:]*):([^:]*):([^:]*):([^\]]*)\]\]`：6 段冒号分隔，首尾 `[[ ]]`，即使 options 为空也保留结尾冒号。
- 7 种合法 type：`text | textarea | select | date | number | photo | photolist`。
- 所有 UI 文案走 i18n（`useI18n()` + `t()`），zh-CN 与 en-US 同步。
- Modal 遵循本目录约定（参考 `TemplateUploadModal.vue`）：prop `visible`，内部 `:open="visible"`，emit `cancel` 关闭。
- 前端无单测框架（package.json 只有 `build`/`dev`）。验证手段 = `npx vue-tsc --noEmit` 类型检查 + 手动 UAT，并把用例写入 `doc/02-工作进度/测试文档.md`（CLAUDE.md 第 3 节：AI 不得将用例标为「通过」）。
- 提交分别进入 `frontend/` git 仓库与 `doc/` git 仓库。

## File Structure

- **Create** `frontend/src/views/settings/components/PlaceholderGeneratorModal.vue` — 自包含弹窗：表单 + 实时预览 + 复制 + 重置。唯一职责：生成单个占位符字符串。
- **Modify** `frontend/src/views/settings/components/TemplateManagement.vue` — 在卡片 `#extra` 加「占位符生成器」按钮，持有 `placeholderGeneratorVisible` ref，渲染弹窗。
- **Modify** `frontend/src/i18n/locales/zh-CN.ts` 与 `en-US.ts` — `settings` 命名空间下新增 `pg*` 系列 key。
- **Modify** `doc/02-工作进度/测试文档.md` — 新增 UAT 用例。

---

### Task 1: 新增 i18n keys（zh-CN + en-US）

**Files:**
- Modify: `frontend/src/i18n/locales/zh-CN.ts`（`settings:` 块内，`supportFormat: '支持 .xlsx, .xls 格式',` 这一行之后插入）
- Modify: `frontend/src/i18n/locales/en-US.ts`（对应 `settings:` 块内同样位置）

**Interfaces:**
- Produces: `settings.pg*` 系列 i18n key，供 Task 2/3 的 `t()` 调用引用。完整 key 清单见下方代码（zh 与 en 一一对应）。

- [ ] **Step 1: 在 zh-CN.ts 的 settings 块插入中文 key**

定位到第 279 行 `supportFormat: '支持 .xlsx, .xls 格式',`，在其后插入：

```ts
    // 占位符生成器
    pgGenerator: '占位符生成器',
    pgTitle: '占位符生成器',
    pgFieldType: '字段类型',
    pgFieldName: '字段名',
    pgFieldNamePlaceholder: '英文标识，如 noiseType',
    pgFieldNameIllegal: '字段名不能包含 : [ ]',
    pgFieldNameRequired: '请填写字段名',
    pgLabelZh: '中文标签',
    pgLabelEn: '英文标签',
    pgRequired: '必填',
    pgOptions: '选项',
    pgOptionsPlaceholder: '英文逗号分隔，如 B,C,S,O',
    pgOptionsHint: 'select 类型建议填写选项',
    pgPreview: '预览',
    pgCopy: '复制',
    pgReset: '重置',
    pgCopySuccess: '已复制到剪贴板',
    pgCopyFailed: '复制失败，请手动选中预览区复制',
    pgTypeText: '文本',
    pgTypeTextarea: '多行文本',
    pgTypeSelect: '下拉',
    pgTypeDate: '日期',
    pgTypeNumber: '数字',
    pgTypePhoto: '图片',
    pgTypePhotolist: '多图',
```

- [ ] **Step 2: 在 en-US.ts 的 settings 块插入对应英文 key**

定位到 `supportFormat: 'Supports .xlsx, .xls',`（en-US settings 块内）这一行，在其后插入（key 顺序与 zh 完全一致）：

```ts
    // Placeholder Generator
    pgGenerator: 'Placeholder Generator',
    pgTitle: 'Placeholder Generator',
    pgFieldType: 'Field Type',
    pgFieldName: 'Field Name',
    pgFieldNamePlaceholder: 'English identifier, e.g. noiseType',
    pgFieldNameIllegal: 'Field name cannot contain : [ ]',
    pgFieldNameRequired: 'Please fill in field name',
    pgLabelZh: 'Chinese Label',
    pgLabelEn: 'English Label',
    pgRequired: 'Required',
    pgOptions: 'Options',
    pgOptionsPlaceholder: 'Comma-separated, e.g. B,C,S,O',
    pgOptionsHint: 'Options recommended for select type',
    pgPreview: 'Preview',
    pgCopy: 'Copy',
    pgReset: 'Reset',
    pgCopySuccess: 'Copied to clipboard',
    pgCopyFailed: 'Copy failed, please select preview text manually',
    pgTypeText: 'Text',
    pgTypeTextarea: 'Textarea',
    pgTypeSelect: 'Select',
    pgTypeDate: 'Date',
    pgTypeNumber: 'Number',
    pgTypePhoto: 'Photo',
    pgTypePhotolist: 'Photo List',
```

- [ ] **Step 3: 类型检查**

Run（在 `frontend/` 下）: `npx vue-tsc --noEmit`
Expected: 无新增错误（已有项目错误若有，保持不变即可；关注与本任务无关的 `settings.pg*` 报错为零）。

- [ ] **Step 4: 提交**

```bash
cd frontend
git add src/i18n/locales/zh-CN.ts src/i18n/locales/en-US.ts
git commit -m "feat(i18n): 新增占位符生成器 i18n key"
```

---

### Task 2: 创建 PlaceholderGeneratorModal.vue

**Files:**
- Create: `frontend/src/views/settings/components/PlaceholderGeneratorModal.vue`

**Interfaces:**
- Consumes: Task 1 的 `settings.pg*` i18n key。
- Produces: 一个 Vue 组件，props `{ visible: boolean }`，emits `['cancel']`。供 Task 3 在 `TemplateManagement.vue` 中 `<PlaceholderGeneratorModal :visible="..." @cancel="..." />` 使用。

- [ ] **Step 1: 创建组件文件，写入完整内容**

完整文件内容：

```vue
<template>
  <a-modal
    :open="props.visible"
    :title="t('settings.pgTitle')"
    :width="560"
    :footer="null"
    @cancel="$emit('cancel')"
  >
    <a-form layout="vertical" :model="form">
      <!-- 字段类型：可点击卡片 -->
      <a-form-item :label="t('settings.pgFieldType')">
        <div class="type-cards">
          <div
            v-for="ft in fieldTypes"
            :key="ft.value"
            class="type-card"
            :class="{ active: form.type === ft.value }"
            @click="form.type = ft.value"
          >
            <component :is="ft.icon" />
            <span>{{ t(ft.labelKey) }}</span>
          </div>
        </div>
      </a-form-item>

      <!-- 字段名 -->
      <a-form-item :label="t('settings.pgFieldName')" required>
        <a-input
          v-model:value="form.fieldName"
          :placeholder="t('settings.pgFieldNamePlaceholder')"
        />
        <div v-if="fieldNameError" class="field-error">{{ fieldNameError }}</div>
      </a-form-item>

      <!-- 中文标签 -->
      <a-form-item :label="t('settings.pgLabelZh')">
        <a-input v-model:value="form.labelZh" />
      </a-form-item>

      <!-- 英文标签 -->
      <a-form-item :label="t('settings.pgLabelEn')">
        <a-input v-model:value="form.labelEn" />
      </a-form-item>

      <!-- 必填 -->
      <a-form-item :label="t('settings.pgRequired')">
        <a-switch v-model:checked="form.required" />
      </a-form-item>

      <!-- 选项：仅 select 显示 -->
      <a-form-item v-if="form.type === 'select'" :label="t('settings.pgOptions')">
        <a-input
          v-model:value="form.options"
          :placeholder="t('settings.pgOptionsPlaceholder')"
        />
        <div v-if="!form.options" class="field-warn">{{ t('settings.pgOptionsHint') }}</div>
      </a-form-item>

      <!-- 预览 -->
      <a-form-item :label="t('settings.pgPreview')">
        <div class="preview-box" :class="{ invalid: !isValid }">
          <code>{{ placeholder }}</code>
        </div>
      </a-form-item>

      <!-- 操作 -->
      <div class="actions">
        <a-button @click="handleReset">{{ t('settings.pgReset') }}</a-button>
        <a-button type="primary" :disabled="!isValid" @click="handleCopy">
          {{ t('settings.pgCopy') }}
        </a-button>
      </div>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { reactive, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { message } from 'ant-design-vue'
import {
  EditOutlined,
  AlignLeftOutlined,
  UnorderedListOutlined,
  CalendarOutlined,
  NumberOutlined,
  PictureOutlined,
  FileImageOutlined,
} from '@ant-design/icons-vue'

type FieldType = 'text' | 'textarea' | 'select' | 'date' | 'number' | 'photo' | 'photolist'

interface Props {
  visible: boolean
}
const props = defineProps<Props>()
defineEmits<{ (e: 'cancel'): void }>()

const { t } = useI18n()

const fieldTypes: { value: FieldType; icon: any; labelKey: string }[] = [
  { value: 'text', icon: EditOutlined, labelKey: 'settings.pgTypeText' },
  { value: 'textarea', icon: AlignLeftOutlined, labelKey: 'settings.pgTypeTextarea' },
  { value: 'select', icon: UnorderedListOutlined, labelKey: 'settings.pgTypeSelect' },
  { value: 'date', icon: CalendarOutlined, labelKey: 'settings.pgTypeDate' },
  { value: 'number', icon: NumberOutlined, labelKey: 'settings.pgTypeNumber' },
  { value: 'photo', icon: PictureOutlined, labelKey: 'settings.pgTypePhoto' },
  { value: 'photolist', icon: FileImageOutlined, labelKey: 'settings.pgTypePhotolist' },
]

const form = reactive({
  type: 'text' as FieldType,
  fieldName: '',
  labelZh: '',
  labelEn: '',
  required: false,
  options: '',
})

const ILLEGAL = /[:\[\]]/

const fieldNameError = computed(() => {
  if (!form.fieldName) return ''
  if (ILLEGAL.test(form.fieldName)) return t('settings.pgFieldNameIllegal')
  return ''
})

const isValid = computed(() => !!form.fieldName && !ILLEGAL.test(form.fieldName))

const placeholder = computed(() => {
  const opts = form.type === 'select' ? form.options : ''
  return `[[${form.type}:${form.fieldName}:${form.labelZh}:${form.labelEn}:${form.required}:${opts}]]`
})

const handleCopy = async () => {
  if (!isValid.value) return
  try {
    await navigator.clipboard.writeText(placeholder.value)
    message.success(t('settings.pgCopySuccess'))
  } catch {
    message.error(t('settings.pgCopyFailed'))
  }
}

const handleReset = () => {
  form.type = 'text'
  form.fieldName = ''
  form.labelZh = ''
  form.labelEn = ''
  form.required = false
  form.options = ''
}
</script>

<style lang="less" scoped>
.type-cards {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.type-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 72px;
  padding: 8px 4px;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  cursor: pointer;
  font-size: 12px;
  transition: all 0.2s;
  &:hover {
    border-color: #1890ff;
  }
  &.active {
    border-color: #1890ff;
    background: #e6f7ff;
    color: #1890ff;
  }
  .anticon {
    font-size: 18px;
    margin-bottom: 4px;
  }
}
.field-error {
  margin-top: 4px;
  font-size: 12px;
  color: #ff4d4f;
}
.field-warn {
  margin-top: 4px;
  font-size: 12px;
  color: #faad14;
}
.preview-box {
  padding: 8px 12px;
  background: #f5f5f5;
  border-radius: 4px;
  border: 1px solid #e8e8e8;
  word-break: break-all;
  code {
    font-family: 'Consolas', 'Monaco', monospace;
    font-size: 13px;
    color: #333;
  }
  &.invalid code {
    color: #ff4d4f;
  }
}
.actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
```

说明（实现要点，非占位）：
- `:footer="null"` 用自定义「重置 / 复制」按钮，关闭走 `@cancel`（点遮罩/X 触发）。
- 关闭**不重置** `form`（reactive 状态在组件实例存活期间保留），符合「关闭重开保留输入」的设计；仅「重置」按钮清空。组件实例由 Task 3 常驻 DOM，`v-if` 不销毁，故状态保留。
- `placeholder` computed 对非 select 类型强制 options 段为空字符串，保证语法恒定为 6 段。

- [ ] **Step 2: 类型检查**

Run（在 `frontend/` 下）: `npx vue-tsc --noEmit`
Expected: 无与 `PlaceholderGeneratorModal.vue` 相关的报错。若 `NumberOutlined` 等图标名报不存在，改用 `FieldNumberOutlined`（二者均在 `@ant-design/icons-vue` 中存在，任选其一即可，保持类型通过）。

- [ ] **Step 3: 提交**

```bash
cd frontend
git add src/views/settings/components/PlaceholderGeneratorModal.vue
git commit -m "feat: 新增占位符生成器弹窗组件"
```

---

### Task 3: 接入 TemplateManagement.vue

**Files:**
- Modify: `frontend/src/views/settings/components/TemplateManagement.vue`

**Interfaces:**
- Consumes: Task 2 的 `PlaceholderGeneratorModal` 组件（props `visible`，emit `cancel`）；Task 1 的 `settings.pgGenerator` key。
- Produces: 模板管理卡片 `#extra` 多一个「占位符生成器」按钮，点击打开弹窗。

- [ ] **Step 1: 在 `#extra` 槽加按钮**

将 `<template>...</template>` 开头的 `#extra` 块（第 4-8 行）：

```vue
      <template #extra>
        <a-button type="primary" @click="$emit('add-template')">
          <PlusOutlined /> {{ t('settings.uploadTemplate') }}
        </a-button>
      </template>
```

替换为（在原按钮后追加生成器按钮，并在卡片外渲染弹窗）：

```vue
      <template #extra>
        <a-space>
          <a-button @click="placeholderGeneratorVisible = true">
            <ThunderboltOutlined /> {{ t('settings.pgGenerator') }}
          </a-button>
          <a-button type="primary" @click="$emit('add-template')">
            <PlusOutlined /> {{ t('settings.uploadTemplate') }}
          </a-button>
        </a-space>
      </template>
```

- [ ] **Step 2: 在 `</a-card>` 之后、`</div>` 之前渲染弹窗**

将模板结尾：

```vue
    </a-card>
  </div>
</template>
```

改为：

```vue
    </a-card>

    <PlaceholderGeneratorModal
      :visible="placeholderGeneratorVisible"
      @cancel="placeholderGeneratorVisible = false"
    />
  </div>
</template>
```

- [ ] **Step 3: 在 `<script setup>` 中加 ref + import**

在 import 区（第 30-33 行附近）追加图标与组件 import。将：

```ts
import { PlusOutlined } from '@ant-design/icons-vue'
import { useUserNameMap } from '@/composables/useUserNameMap'
```

改为：

```ts
import { PlusOutlined, ThunderboltOutlined } from '@ant-design/icons-vue'
import { useUserNameMap } from '@/composables/useUserNameMap'
import PlaceholderGeneratorModal from './PlaceholderGeneratorModal.vue'
```

在 `const { t } = useI18n()` 一行之前，新增可见性 ref：

```ts
import { ref } from 'vue'
```
（注意：第 30 行已有 `import { computed } from 'vue'`，将其合并为 `import { computed, ref } from 'vue'`，不要重复 import。）

然后在 `const { t } = useI18n()` 之后新增：

```ts
const placeholderGeneratorVisible = ref(false)
```

- [ ] **Step 4: 类型检查**

Run（在 `frontend/` 下）: `npx vue-tsc --noEmit`
Expected: 无报错（确认 `ThunderboltOutlined`、`PlaceholderGeneratorModal`、`placeholderGeneratorVisible` 均解析成功）。

- [ ] **Step 5: 提交**

```bash
cd frontend
git add src/views/settings/components/TemplateManagement.vue
git commit -m "feat: 模板管理页接入占位符生成器入口"
```

---

### Task 4: 手动验证 + UAT 用例入档

**Files:**
- Modify: `doc/02-工作进度/测试文档.md`

**Interfaces:**
- Consumes: Task 1-3 完成的功能。

- [ ] **Step 1: 启动前端手动验证**

Run（在 `frontend/` 下）: `npm run dev`
打开浏览器到设置 → 精分析模板 tab，依次验证（人工执行）：

1. 卡片右上角出现「占位符生成器」按钮，点击打开弹窗。
2. 选 `select` 类型，字段名填 `responsibility`，中文 `责任判定`，英文 `Responsibility`，必填开，选项 `B,C,S,O` → 预览显示 `[[select:responsibility:责任判定:Responsibility:true:B,C,S,O]]`，点复制后有成功提示，粘贴到记事本确认内容正确。
3. 字段名填 `a:b` → 出现红色非法字符提示，复制按钮禁用，预览变红。
4. 字段名留空 → 复制按钮禁用。
5. 切到 `photo` 类型 → 选项输入框消失，预览为 `[[photo:xxx::::false:]]`（options 段空但仍 6 段）。
6. select 类型清空选项 → 出现黄色「建议填写选项」提示，但仍可复制。
7. 关闭弹窗再打开 → 上次输入保留；点「重置」→ 全部清空回到 text。

Expected: 全部符合。

- [ ] **Step 2: 把用例写入测试文档**

在 `doc/02-工作进度/测试文档.md` 的「测试完成进度」表格中新增 7 条用例（对应 Step 1 的 1-7），状态留「未执行」（**不得**标为「通过」，除非人类明确指令）。每条用例包含：编号、标题、前置条件、步骤、预期、状态、日期（日期留空，由人工标通过时补）。

具体用例文本（按文档现有表格格式追加行）：

| 编号 | 标题 | 前置 | 步骤 | 预期 | 状态 | 日期 |
|---|---|---|---|---|---|---|
| PG-01 | 打开占位符生成器 | 进入设置-精分析模板 | 点「占位符生成器」按钮 | 弹窗正常打开 | 未执行 | |
| PG-02 | 生成 select 占位符并复制 | PG-01 | 选 select，填 responsibility/责任判定/Responsibility/必填/B,C,S,O，点复制 | 预览 `[[select:responsibility:责任判定:Responsibility:true:B,C,S,O]]`，复制成功 | 未执行 | |
| PG-03 | 字段名非法字符校验 | PG-01 | 字段名填 `a:b` | 红色提示，复制禁用，预览标红 | 未执行 | |
| PG-04 | 字段名为空禁用复制 | PG-01 | 字段名留空 | 复制按钮禁用 | 未执行 | |
| PG-05 | 非 select 类型隐藏选项 | PG-01 | 切 photo 类型 | 选项输入框消失，预览仍为 6 段 | 未执行 | |
| PG-06 | select 空选项警告 | PG-01 | select 且选项为空 | 黄色提示，仍可复制 | 未执行 | |
| PG-07 | 关闭重开保留输入 / 重置 | PG-01 | 输入后关闭重开；再点重置 | 重开保留，重置清空 | 未执行 | |

- [ ] **Step 3: 提交**

```bash
cd doc
git add "02-工作进度/测试文档.md"
git commit -m "test: 新增占位符生成器 UAT 用例 PG-01~07"
```

---

## Self-Review

- **Spec coverage**：放置（TemplateManagement 入口）→ Task 3；组件接口（visible/cancel）→ Task 2；7 类型可点击卡片 → Task 2 fieldTypes；fieldName 非法校验 → Task 2 fieldNameError/isValid；select 才显示选项 → Task 2 `v-if`；实时预览 → Task 2 placeholder computed；复制 + 成功提示 → Task 2 handleCopy；select 空选项黄色提示 → Task 2 field-warn；关闭保留输入 → Task 2 说明（组件常驻不销毁）；重置按钮 → Task 2 handleReset；i18n → Task 1；测试用例 → Task 4。设计文档每节均有任务覆盖。
- **Placeholder scan**：无 TBD/TODO；所有代码块为完整可粘贴内容。
- **Type consistency**：`FieldType`、`fieldTypes`、`form`、`placeholder`、`isValid`、`fieldNameError`、`handleCopy`、`handleReset`、`placeholderGeneratorVisible` 跨任务命名一致；emit `cancel` 与 prop `visible` 在 Task 2 定义、Task 3 消费一致。
