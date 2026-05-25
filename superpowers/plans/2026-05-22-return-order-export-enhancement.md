# 退件列表导出增强 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enhance the return order export to include Part details (40-column flat table) with configurable row-limit threshold.

**Architecture:** Reuse existing export flow (controller → service → excelHandler). Add `ExportProperties` config class for threshold. Rewrite `ReturnOrderExcelHandler.exportToExcel()` to accept a flat list of order+part rows. Service queries orders with their parts, counts parts against threshold, then delegates to handler.

**Tech Stack:** Spring Boot, Apache POI (XSSFWorkbook), Vue 3 + Ant Design Vue

---

## File Map

| Action | File | Responsibility |
|--------|------|---------------|
| Create | `backend/.../config/ExportProperties.java` | Bind `aftermarket-parts.export.max-rows` from yml |
| Modify | `backend/.../service/ReturnOrderService.java:245-260` | Count parts, threshold check, query orders+parts |
| Rewrite | `backend/.../service/excel/ReturnOrderExcelHandler.java:34-45` | 40-column flat export |
| Modify | `backend/.../resources/application.yml` | Add export config section |
| Modify | `frontend/src/views/return-orders/OrderList.vue:handleExport` | Show Modal.warning on threshold error |
| Modify | `frontend/src/services/returnOrderApi.ts:exportExcel` | Handle 400 with error message extraction |

---

### Task 1: Add ExportProperties configuration class

**Files:**
- Create: `backend/src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/config/ExportProperties.java`

- [ ] **Step 1: Create ExportProperties.java**

```java
package com.bosch.rbcc.aftermarketpartsmanagementsystem.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "aftermarket-parts.export")
public class ExportProperties {

    private int maxRows = 10000;
}
```

- [ ] **Step 2: Add config to application.yml**

Append after the `logging:` block in `backend/src/main/resources/application.yml`:

```yaml
aftermarket-parts:
  export:
    max-rows: 10000
```

- [ ] **Step 3: Commit**

```bash
cd backend
git add src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/config/ExportProperties.java src/main/resources/application.yml
git commit -m "feat(export): add configurable export max-rows threshold"
```

---

### Task 2: Rewrite ReturnOrderExcelHandler for 40-column flat export

**Files:**
- Modify: `backend/src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/service/excel/ReturnOrderExcelHandler.java`

The handler receives a flat list of `ExportRow` objects (each row = one Part + its ReturnOrder header info).

- [ ] **Step 1: Add ExportRow inner record and rewrite exportToExcel**

Replace the entire content of `ReturnOrderExcelHandler.java` with:

```java
package com.bosch.rbcc.aftermarketpartsmanagementsystem.service.excel;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.PartDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.ReturnOrderDTO;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReturnOrderExcelHandler {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // 40 columns in exact spec order
    private static final String[] HEADERS = {
            // 退件单 (11)
            "退货单号", "客户", "收货日期", "投诉日期", "退货方式", "物流单号",
            "退货数量", "投诉类型", "退货单状态", "退货单创建人", "退货单创建时间",
            // 零件信息 (13)
            "退件编号", "零件代码(FIS)", "事业群", "产品平台", "生产班次",
            "客户故障类型", "博世故障类型", "零件状态", "是否取样",
            "QC编号", "责任工程师", "分析师", "投诉位置",
            // 车辆信息 (5)
            "车辆生产日期", "车辆购买日期", "车辆故障日期", "VIN", "里程(km)",
            // 描述 (3)
            "客户描述", "其他描述", "维修站",
            // 审计 (4)
            "零件创建人", "零件创建时间", "零件更新人", "零件更新时间",
            // 汇总 (4)
            "初始分析数量", "精细分析数量", "报废数量", "QC已创建数量"
    };

    private static final Map<String, String> ORDER_STATUS_MAP = Map.of(
            "draft", "草稿", "submitted", "已提交", "scrapped", "已报废"
    );

    private static final Map<String, String> RETURN_METHOD_MAP = Map.of(
            "express", "快递", "pickup", "自提", "other", "其他"
    );

    private static final Map<String, String> FAILURE_TYPE_MAP = Map.of(
            "NVH", "NVH", "APPEARANCE", "外观", "FUNCTION", "功能"
    );

    /**
     * Exports a flat list of order+part rows to Excel.
     */
    public byte[] exportToExcel(List<ExportRow> rows) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("退件明细");
            CellStyle headerStyle = createHeaderStyle(wb);
            createHeaderRow(sheet, headerStyle);
            fillDataRows(sheet, rows);
            autoSizeColumns(sheet);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ExcelOperationException("Excel export failed: " + e.getMessage(), e);
        }
    }

    private CellStyle createHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private void createHeaderRow(Sheet sheet, CellStyle headerStyle) {
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < HEADERS.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(HEADERS[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    private void fillDataRows(Sheet sheet, List<ExportRow> rows) {
        int rowIdx = 1;
        for (ExportRow row : rows) {
            Row excelRow = sheet.createRow(rowIdx++);
            fillExportRow(excelRow, row);
        }
    }

    private void fillExportRow(Row r, ExportRow d) {
        // 退件单 (0-10)
        r.createCell(0).setCellValue(s(d.order().getOrderNumber()));
        r.createCell(1).setCellValue(s(d.order().getCustomer()));
        r.createCell(2).setCellValue(s(d.order().getReceiveDate()));
        r.createCell(3).setCellValue(s(d.order().getComplaintDate()));
        r.createCell(4).setCellValue(map(RETURN_METHOD_MAP, d.order().getReturnMethod()));
        r.createCell(5).setCellValue(s(d.order().getTrackingNumber()));
        setIntCell(r, 6, d.order().getReturnQuantity());
        r.createCell(7).setCellValue(s(d.order().getComplaintType()));
        r.createCell(8).setCellValue(map(ORDER_STATUS_MAP, d.order().getStatus()));
        r.createCell(9).setCellValue(s(d.order().getCreatedBy()));
        r.createCell(10).setCellValue(formatDateTime(d.order().getCreatedAt()));

        // 零件信息 (11-23)
        r.createCell(11).setCellValue(s(d.part().getPartNumber()));
        r.createCell(12).setCellValue(s(d.part().getPartCode()));
        r.createCell(13).setCellValue(s(d.part().getBusinessUnit()));
        r.createCell(14).setCellValue(s(d.part().getProductPlatform()));
        r.createCell(15).setCellValue(s(d.part().getProductionShift()));
        r.createCell(16).setCellValue(map(FAILURE_TYPE_MAP, d.part().getFailureType()));
        r.createCell(17).setCellValue(s(d.part().getBoschFailureType()));
        r.createCell(18).setCellValue(s(d.part().getStatus()));
        r.createCell(19).setCellValue(d.part().getIsSample() != null && d.part().getIsSample() == 1 ? "是" : "否");
        r.createCell(20).setCellValue(s(d.part().getQcNo()));
        r.createCell(21).setCellValue(s(d.part().getResponsibleEngineer()));
        r.createCell(22).setCellValue(s(d.part().getAnalyst()));
        r.createCell(23).setCellValue(s(d.part().getComplaintLocation()));

        // 车辆信息 (24-28)
        r.createCell(24).setCellValue(s(d.part().getVehicleProductionDate()));
        r.createCell(25).setCellValue(s(d.part().getVehiclePurchaseDate()));
        r.createCell(26).setCellValue(s(d.part().getVehicleFailureDate()));
        r.createCell(27).setCellValue(s(d.part().getVehicleVIN()));
        setIntCell(r, 28, d.part().getVehicleMileage());

        // 描述 (29-31)
        r.createCell(29).setCellValue(s(d.part().getCustomerDescription()));
        r.createCell(30).setCellValue(s(d.part().getOtherDescription()));
        r.createCell(31).setCellValue(s(d.part().getRepairStation()));

        // 审计 (32-35)
        r.createCell(32).setCellValue(s(d.part().getCreatedBy()));
        r.createCell(33).setCellValue(formatDateTime(d.part().getCreatedAt()));
        r.createCell(34).setCellValue(s(d.part().getUpdatedBy()));
        r.createCell(35).setCellValue(formatDateTime(d.part().getUpdatedAt()));

        // 汇总 (36-39)
        setIntCell(r, 36, d.order().getInitialAnalysisQuantity());
        setIntCell(r, 37, d.order().getDetailedAnalysisQuantity());
        setIntCell(r, 38, d.order().getScrappedQuantity());
        setIntCell(r, 39, d.order().getQcCreatedQuantity());
    }

    private static void setIntCell(Row r, int col, Integer val) {
        if (val != null) {
            r.createCell(col).setCellValue(val);
        }
    }

    private static String s(String val) {
        return val != null ? val : "";
    }

    private static String map(Map<String, String> m, String key) {
        if (key == null) return "";
        return m.getOrDefault(key, key);
    }

    private static String formatDateTime(String val) {
        if (val == null || val.isBlank()) return "";
        try {
            if (val.length() == 10) return val; // already yyyy-MM-dd
            return LocalDateTime.parse(val, DateTimeFormatter.ISO_LOCAL_DATE_TIME).format(DATETIME_FMT);
        } catch (Exception e) {
            return val;
        }
    }

    private void autoSizeColumns(Sheet sheet) {
        for (int i = 0; i < HEADERS.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    // --- Import methods (unchanged) ---

    public List<ReturnOrderDTO> importFromExcel(MultipartFile file) {
        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            return parseOrdersFromSheet(sheet);
        } catch (IOException e) {
            throw new ExcelOperationException("Failed to parse Excel file: " + e.getMessage(), e);
        }
    }

    private List<ReturnOrderDTO> parseOrdersFromSheet(Sheet sheet) {
        List<ReturnOrderDTO> orders = new ArrayList<>();
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            try {
                orders.add(parseOrderFromRow(row));
            } catch (Exception e) {
                // Skip invalid rows
            }
        }
        return orders;
    }

    private ReturnOrderDTO parseOrderFromRow(Row row) {
        return ReturnOrderDTO.builder()
                .customer(getCellString(row, 0))
                .receiveDate(getCellString(row, 1))
                .complaintDate(getCellString(row, 2))
                .returnMethod(getCellString(row, 3))
                .trackingNumber(getCellString(row, 4))
                .returnQuantity((int) row.getCell(5).getNumericCellValue())
                .build();
    }

    private String getCellString(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            default -> null;
        };
    }

    /**
     * Flat export row combining ReturnOrder header + single Part detail.
     */
    public record ExportRow(ReturnOrderDTO order, PartDTO part) {}

    public static class ExcelOperationException extends RuntimeException {
        public ExcelOperationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
cd backend
git add src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/service/excel/ReturnOrderExcelHandler.java
git commit -m "feat(export): rewrite Excel handler with 40-column flat export"
```

---

### Task 3: Rewrite ReturnOrderService.exportToExcel with threshold check

**Files:**
- Modify: `backend/src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/service/ReturnOrderService.java`

- [ ] **Step 1: Add ExportProperties injection**

At the top of `ReturnOrderService`, add `ExportProperties` to the injected fields. Change:

```java
    private final ReturnOrderExcelHandler excelHandler;
    private final EntityManager entityManager;
```

to:

```java
    private final ReturnOrderExcelHandler excelHandler;
    private final ExportProperties exportProperties;
    private final EntityManager entityManager;
```

Add the import:

```java
import com.bosch.rbcc.aftermarketpartsmanagementsystem.config.ExportProperties;
```

- [ ] **Step 2: Replace exportToExcel method**

Replace the `exportToExcel` method (lines 245-260) and the `EXPORT_MAX_ROWS` constant (line 243) with:

```java
    public byte[] exportToExcel(String orderNumber, String customer, String status,
                                 String receiveDateStart, String receiveDateEnd) {
        List<String> statuses = (status != null && !status.isBlank()) ? List.of(status) : null;

        // Build the order filter predicates once
        var orderSpec = buildOrderSpec(orderNumber, customer, statuses, receiveDateStart, receiveDateEnd);

        // Count total parts across matching orders
        List<ReturnOrder> matchingOrders = orderRepo.findAll(orderSpec);
        List<String> orderIds = matchingOrders.stream().map(ReturnOrder::getId).toList();
        long totalParts = orderIds.isEmpty() ? 0 : partRepo.countByIdIn(orderIds);

        int maxRows = exportProperties.getMaxRows();
        if (totalParts > maxRows) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "导出数据量（" + totalParts + " 条）超过上限 " + maxRows + " 条，请缩小筛选条件范围");
        }

        // Build flat export rows: each Part + its ReturnOrder header
        List<ReturnOrderExcelHandler.ExportRow> rows = new ArrayList<>();
        Map<String, ReturnOrderDTO> orderDtoCache = new LinkedHashMap<>();
        for (ReturnOrder order : matchingOrders) {
            ReturnOrderDTO orderDto = orderDtoCache.computeIfAbsent(order.getId(),
                    id -> toDTO(orderRepo.findById(id).orElseThrow()));
            List<Part> parts = partRepo.findByOrderId(order.getId());
            for (Part part : parts) {
                rows.add(new ReturnOrderExcelHandler.ExportRow(orderDto, toPartDTO(part)));
            }
        }

        return excelHandler.exportToExcel(rows);
    }

    private jakarta.persistence.criteria.Predicate buildOrderPredicate(
            jakarta.persistence.criteria.CriteriaBuilder cb,
            jakarta.persistence.criteria.Root<ReturnOrder> root,
            String orderNumber, String customer, List<String> statuses,
            String receiveDateStart, String receiveDateEnd) {
        List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
        if (orderNumber != null && !orderNumber.isBlank()) {
            predicates.add(cb.like(cb.upper(root.get("orderNumber")), "%" + orderNumber.toUpperCase() + "%"));
        }
        if (customer != null && !customer.isBlank()) {
            predicates.add(cb.or(
                cb.equal(root.get("customerId"), customer),
                cb.equal(root.get("customer"), customer)
            ));
        }
        if (statuses != null && !statuses.isEmpty()) {
            predicates.add(root.get("status").in(statuses));
        }
        if (receiveDateStart != null && !receiveDateStart.isBlank()) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("receiveDate"), LocalDate.parse(receiveDateStart)));
        }
        if (receiveDateEnd != null && !receiveDateEnd.isBlank()) {
            predicates.add(cb.lessThanOrEqualTo(root.get("receiveDate"), LocalDate.parse(receiveDateEnd)));
        }
        return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
    }

    private org.springframework.data.jpa.domain.Specification<ReturnOrder> buildOrderSpec(
            String orderNumber, String customer, List<String> statuses,
            String receiveDateStart, String receiveDateEnd) {
        return (root, query, cb) -> buildOrderPredicate(cb, root, orderNumber, customer, statuses, receiveDateStart, receiveDateEnd);
    }
```

Also add the missing import for `LinkedHashMap` if not already present:

```java
import java.util.LinkedHashMap;
```

- [ ] **Step 3: Add countByIdIn to PartRepository**

Add this method to `PartRepository.java`:

```java
    long countByIdIn(List<String> ids);
```

Wait — this counts Parts by their own IDs, but we need to count parts by `orderId`. Replace with:

```java
    long countByOrderIdIn(List<String> orderIds);
```

- [ ] **Step 4: Commit**

```bash
cd backend
git add src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/service/ReturnOrderService.java src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/repository/PartRepository.java
git commit -m "feat(export): add parts-based threshold check and flat row assembly"
```

---

### Task 4: Update controller export filename

**Files:**
- Modify: `backend/src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/controller/returnorder/ReturnOrderController.java`

- [ ] **Step 1: Change filename from ReturnOrders to 退件明细**

In the export endpoint (around line 123), change:

```java
    String filename = "ReturnOrders_" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".xlsx";
```

to:

```java
    String filename = URLEncoder.encode("退件明细_" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE), java.nio.charset.StandardCharsets.UTF_8) + ".xlsx";
```

Add import:

```java
import java.net.URLEncoder;
```

- [ ] **Step 2: Commit**

```bash
cd backend
git add src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/controller/returnorder/ReturnOrderController.java
git commit -m "feat(export): update filename to Chinese 退件明细"
```

---

### Task 5: Update frontend error handling for threshold

**Files:**
- Modify: `frontend/src/views/return-orders/OrderList.vue`

- [ ] **Step 1: Change error display from message.error to Modal.warning**

In `OrderList.vue`, find the `handleExport` function. Replace the catch block's `message.error(errMsg, 6)` with `Modal.warning`. The existing code already imports `Modal` from `ant-design-vue`.

Change the catch block (around line that says `message.error(errMsg, 6)`) from:

```typescript
    message.error(errMsg, 6)
```

to:

```typescript
    Modal.warning({
      title: t('returnOrder.exportLimitTitle') || '导出数量超限',
      content: errMsg,
    })
```

Also update the download filename to match the new Chinese name. Change:

```typescript
    link.download = `ReturnOrders_${today}.xlsx`
```

to:

```typescript
    link.download = `退件明细_${today}.xlsx`
```

- [ ] **Step 2: Add i18n key for export limit title**

Find the i18n locale files (search for files matching `**/locales/**/*.ts` or `**/i18n/**/*.ts`). Add the key `returnOrder.exportLimitTitle` with value `"导出数量超限"` in the Chinese locale file. If there's an English locale, add `"Export Limit Exceeded"`.

The exact file path depends on the project's i18n structure — search for an existing key like `returnOrder.title` to find the right file.

- [ ] **Step 3: Commit**

```bash
cd frontend
git add src/views/return-orders/OrderList.vue
git add src/locales/  # or whatever the i18n directory is
git commit -m "feat(export): show Modal.warning on threshold exceeded, update filename"
```

---

### Task 6: Backend build verification

**Files:** None (verification only)

- [ ] **Step 1: Build backend**

```bash
cd C:/Users/XEF1CNG/code/wfam/backend
C:/Users/XEF1CNG/.m2/wrapper/dists/apache-maven-3.9.12-bin/5nmfsn99br87k5d4ajlekdq10k/apache-maven-3.9.12/bin/mvn compile -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 2: Fix any compilation errors**

If the build fails, read the error output, fix the issue, and re-run until clean.

---

### Task 7: Frontend build verification

**Files:** None (verification only)

- [ ] **Step 1: Build frontend**

```bash
cd C:/Users/XEF1CNG/code/wfam/frontend
npx vue-tsc --noEmit
```

Expected: No type errors

- [ ] **Step 2: Fix any type errors**

If the type check fails, fix the issues and re-run until clean.
