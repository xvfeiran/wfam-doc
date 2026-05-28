# WFAM 数据字典 — 全量查询接口

> 更新日期：2026-05-28

本文档整理了系统中三类基础数据（零件号/客户/失效类型）的全量查询方式，供前端对接和数据维护参考。

---

## 1. 零件号 — BU — 产品平台

### 数据模型

| 字段 | 数据库列 | 类型 | 说明 |
|---|---|---|---|
| `id` | `ID` | String(36) | 主键 |
| `partCode` | `PART_CODE` | String(100) | 零件号（唯一） |
| `businessUnit` | `BUSINESS_UNIT` | String(100) | 事业部（BU） |
| `productPlatform` | `PRODUCT_PLATFORM` | String(100) | 产品平台 |

### API 接口

#### 全量查询（不分页）

```
GET /api/v1/part-codes
```

**返回示例：**

```json
[
  {
    "id": "uuid-xxx",
    "partCode": "0123456789",
    "businessUnit": "BU-ABC",
    "productPlatform": "Platform-X",
    "createdAt": "2026-01-01T00:00:00"
  }
]
```

#### 分页查询

```
GET /api/v1/part-codes/page?partCode=&businessUnit=&page=0&size=20&sortBy=partCode&sortOrder=asc
```

| 参数 | 必填 | 说明 |
|---|---|---|
| `partCode` | 否 | 按零件号模糊搜索 |
| `businessUnit` | 否 | 按BU筛选 |
| `page` | 否 | 页码，默认 0 |
| `size` | 否 | 每页条数，默认 10 |
| `sortBy` | 否 | 排序字段 |
| `sortOrder` | 否 | `asc` / `desc` |

#### 按零件号精确查询（自动填充场景）

```
GET /api/v1/part-codes/by-part-code?partCode=0123456789
```

> 用途：售后件录入表单中，输入零件号后自动带出 BU 和产品平台。

#### 获取去重后的 BU 列表

```
GET /api/v1/lookups/business-units
```

**返回示例：** `["BU-ABC", "BU-DEF", "BU-GHI"]`

#### 获取去重后的产品平台列表

```
GET /api/v1/lookups/product-platforms
```

**返回示例：** `["Platform-X", "Platform-Y", "Platform-Z"]`

---

## 2. 客户

### 数据模型

| 字段 | 数据库列 | 类型 | 说明 |
|---|---|---|---|
| `id` | `ID` | String(36) | 主键 |
| `name` | `NAME` | String(100) | 客户名称（唯一，必填） |
| `code` | `CODE` | String(50) | 客户代码（唯一，选填） |

### API 接口

#### 全量查询（不分页，下拉用）

```
GET /api/v1/customers
```

**返回示例：**

```json
[
  {
    "id": "uuid-xxx",
    "name": "Customer A",
    "code": "CA001"
  }
]
```

#### 分页查询

```
GET /api/v1/customers/page?name=&code=&page=0&size=20&sortBy=name&sortOrder=asc
```

| 参数 | 必填 | 说明 |
|---|---|---|
| `name` | 否 | 按名称模糊搜索 |
| `code` | 否 | 按代码模糊搜索 |
| `page` | 否 | 页码，默认 0 |
| `size` | 否 | 每页条数，默认 10 |
| `sortBy` | 否 | 排序字段 |
| `sortOrder` | 否 | `asc` / `desc` |

#### 按ID查询详情

```
GET /api/v1/customers/{id}
```

#### 创建 / 更新

```
POST /api/v1/customers          # 创建
PUT  /api/v1/customers/{id}     # 更新
```

**请求体：**

```json
{
  "name": "Customer A",
  "code": "CA001"
}
```

---

## 3. 失效类型（枚举）

失效类型为系统内置枚举，定义在 `FailureType.java` 中，共 **3 个值**：

| 枚举值 | Code | 中文含义 | 说明 |
|---|---|---|---|
| `NVH` | `NVH` | NVH | Noise, Vibration, Harshness — 噪声、振动与声振粗糙度 |
| `APPEARANCE` | `APPEARANCE` | 外观 | 外观类缺陷 |
| `FUNCTION` | `FUNCTION` | 功能 | 功能性失效 |

### API 接口

#### 通过 Lookup 获取全部失效类型

```
GET /api/v1/lookups/failure-types
```

**返回示例：** `["NVH", "APPEARANCE", "FUNCTION"]`

#### 一次性获取所有下拉数据（含失效类型）

```
GET /api/v1/lookups
```

**返回示例：**

```json
{
  "customers": ["Customer A", "Customer B"],
  "businessUnits": ["BU-ABC", "BU-DEF"],
  "productPlatforms": ["Platform-X", "Platform-Y"],
  "productCategories": [],
  "failureTypes": ["NVH", "APPEARANCE", "FUNCTION"]
}
```

---

## 附录：接口总览

| 数据 | 全量查询 | 分页查询 | 数据来源 |
|---|---|---|---|
| 零件号-BU-产品平台 | `GET /api/v1/part-codes` | `GET /api/v1/part-codes/page` | `APMS_PART_CODE` 表 |
| 客户 | `GET /api/v1/customers` | `GET /api/v1/customers/page` | `APMS_CUSTOMER` 表 |
| 失效类型 | `GET /api/v1/lookups/failure-types` | — | `FailureType` 枚举 |
| BU 去重列表 | `GET /api/v1/lookups/business-units` | — | `APMS_PART_CODE` 表 |
| 产品平台去重列表 | `GET /api/v1/lookups/product-platforms` | — | `APMS_PART_CODE` 表 |
| 全部下拉一次性 | `GET /api/v1/lookups` | — | 聚合 |
