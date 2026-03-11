# 客诉信息卡OCR识别API文档

## 概述

本文档描述了客诉信息卡OCR识别接口的规范。该接口用于通过AI识别客诉信息卡照片，自动提取车辆信息和客户失效描述。

## 接口信息

- **接口名称**: 客诉信息卡OCR识别
- **请求路径**: `/api/v1/ocr/recognize`
- **请求方法**: `POST`
- **Content-Type**: `multipart/form-data`
- **认证方式**: Bearer Token

## 请求参数

### Headers

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| Authorization | String | 是 | 认证Token，格式: `Bearer {token}` |

### Body (multipart/form-data)

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| file | File | 是 | 客诉信息卡照片文件，支持jpg、png、jpeg格式，最大10MB |

## 响应格式

### 成功响应 (200 OK)

```json
{
  "vehicleProductionDate": "2025-06-15",
  "vehiclePurchaseDate": "2025-07-20",
  "vehicleFailureDate": "2026-01-10",
  "vehicleVIN": "LSVAB2183E2123456",
  "vehicleMileage": 15234,
  "customerDescription": "发动机异响，怠速不稳"
}
```

### 字段说明

| 字段名 | 类型 | 说明 | 格式 |
|--------|------|------|------|
| vehicleProductionDate | String | 汽车生产日期 | YYYY-MM-DD |
| vehiclePurchaseDate | String | 汽车购买日期 | YYYY-MM-DD |
| vehicleFailureDate | String | 汽车失效日期 | YYYY-MM-DD |
| vehicleVIN | String | 车辆VIN码 | 17位字符 |
| vehicleMileage | Integer | 车辆行驶里程 | 单位：km |
| customerDescription | String | 客户失效描述 | 文本 |

**注意**: 如果某个字段无法识别，该字段返回空字符串 `""` 或 `null`。

## 图片要求

| 项目 | 要求 |
|------|------|
| 格式 | JPG、PNG、JPEG |
| 大小 | 最大10MB |
| 分辨率 | 建议800x600以上 |
| 内容 | 需清晰显示客诉信息卡上的文字信息 |

## 使用示例

### cURL

```bash
curl -X POST \
  'https://api.example.com/api/v1/ocr/recognize' \
  -H 'Authorization: Bearer YOUR_TOKEN' \
  -F 'file=@/path/to/image.jpg'
```

### JavaScript (Fetch)

```javascript
const formData = new FormData();
formData.append('file', fileInput.files[0]);

fetch('https://api.example.com/api/v1/ocr/recognize', {
  method: 'POST',
  headers: {
    'Authorization': 'Bearer YOUR_TOKEN'
  },
  body: formData
})
.then(response => response.json())
.then(data => {
  console.log('识别结果:', data);
});
```

### Axios

```javascript
const formData = new FormData();
formData.append('file', fileInput.files[0]);

axios.post('https://api.example.com/api/v1/ocr/recognize', formData, {
  headers: {
    'Authorization': 'Bearer YOUR_TOKEN',
    'Content-Type': 'multipart/form-data'
  }
})
.then(response => {
  console.log('识别结果:', response.data);
});
```

## 注意事项

1. **处理时间**: OCR识别通常需要5-15秒，建议在UI上显示加载状态
2. **数据校验**: 建议对识别结果进行格式校验（如VIN码长度、日期格式等）
3. **图片预处理**: 建议客户端对图片进行压缩以减少传输时间

## 版本历史

| 版本 | 日期 | 说明 |
|------|------|------|
| 1.0.0 | 2026-03-09 | 初始版本 |
