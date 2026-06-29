# 精分析模板占位符生成器 — 设计文档

日期：2026-06-29

## 1. 背景与目标

精分析模板目前是用户在 Excel 中**手工编辑**的 `.xlsx` 文件，单元格内需写入如下占位符语法：

```
[[type:fieldName:labelZh:labelEn:required:options]]
```

该语法有 6 个冒号分隔段、7 种字段类型，普通用户难以记忆和正确书写。

本功能提供一个**纯前端的占位符生成器弹窗**，让用户通过鼠标点选 + 少量键盘输入即可拼出正确占位符，复制后粘贴回 Excel。

**约束**：保留现有 Excel 模板工作流，**不改动**上传 / 解析 / 导出后端管线。

## 2. 范围

- 仅前端，新增 1 个组件，修改 1 处入口。
- 单次式生成：一次生成一个占位符并复制。
- 不做字段名全局唯一性校验（生成器不感知整个模板内容，由后端解析阶段负责）。

## 3. 放置位置

- 新建组件：`frontend/src/views/settings/components/PlaceholderGeneratorModal.vue`
- 入口：`TemplateManagement.vue` 顶部操作区新增「占位符生成器」按钮，点击打开弹窗。
- 理由：放在用户思考模板的同一位置（Settings → 模板 tab），发现性最好；弹窗形式轻量，适合与 Excel 来回切换使用。

## 4. 组件接口

遵循项目现有 modal 约定：

```ts
props: { visible: boolean }
emits: ['update:visible']
```

不需要 `success` 事件（不写任何数据）。

## 5. 表单交互（核心体验）

弹窗内一个竖向表单，从上到下：

1. **字段类型**：7 个可点击卡片/标签按钮，带图标 + 中文名：
   - `text` 文本、`textarea` 多行文本、`select` 下拉、`date` 日期、`number` 数字、`photo` 单张图片、`photolist` 多张图片。
   - 点击高亮选中。
2. **字段名 (fieldName)**：单行输入，必填。校验：不能含 `:` `[` `]`（会破坏语法）。
3. **中文标签 (labelZh)**：单行输入。
4. **英文标签 (labelEn)**：单行输入。
5. **是否必填**：开关，默认 `false`。
6. **选项 (options)**：**仅当类型为 `select` 时显示**。单行输入，提示用英文逗号分隔（如 `B,C,S,O`）。

全部文案走 i18n，复用现有 `useI18n()` + `t()` 模式。

## 6. 预览 & 复制

- 表单下方实时预览区，等宽字体显示拼好的字符串，例如：
  ```
  [[select:responsibility:责任判定:Responsibility:true:B,C,S,O]]
  ```
- 任何输入变化即时刷新预览。
- 「复制」按钮调用 `navigator.clipboard.writeText`，成功后用项目现有 message 组件给轻提示。
- fieldName 为空或校验不通过时：复制按钮禁用，预览区标红提示缺失项。

## 7. 边界情况

- **select 未填选项**：仍生成（options 段为空），但给黄色提示「select 类型建议填写选项」。
- **photo / photolist / date / number**：不显示选项输入，options 段为空。
- **关闭弹窗保留输入**：用户在 Excel 和弹窗间反复切换，关闭再打开应**保留**上次输入。仅「重置」按钮清空。
- 不做字段名唯一性校验。

## 8. 拼接规则

将 6 段按 `:` 拼接，首尾加 `[[ ]]`。注意 options 段即使为空也要保留结尾冒号结构，与后端正则 `[[type:fieldName:labelZh:labelEn:required:options]]` 严格一致：

```
[[<type>:<fieldName>:<labelZh>:<labelEn>:<required>:<options>]]
```

其中 `required` 输出 `true` / `false` 字符串。

## 9. i18n

新增中英文 key，覆盖：弹窗标题、按钮文案、7 种类型名称、字段标签、提示语、复制成功提示。

## 10. 测试

更新 `doc/02-工作进度/测试文档.md` 的「测试完成进度」表格，新增用例覆盖：
- 各类型正常生成
- select 带/不带选项
- fieldName 含非法字符时禁用复制
- 关闭重开保留输入
- 复制成功提示

（按 CLAUDE.md 第 3 节：AI 不得将用例状态标为「通过」，除非人类明确指令；仅创建用例。）
