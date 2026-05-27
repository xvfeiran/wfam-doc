# 退货单阶段闸口 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move analysis order creation from part-creation time to return-order-submit time, so that parts can only be added in `draft` status and analysis orders are batch-created on submit.

**Architecture:** The `submitted` status becomes a phase gate — `draft` allows part creation (no analysis orders), `submit()` batch-creates analysis orders grouped by analyst and blocks further part additions. Import flow is unchanged.

**Tech Stack:** Spring Boot (Java 21), Vue 3 + TypeScript, Ant Design Vue

---

## File Structure

| Action | File | Responsibility |
|--------|------|----------------|
| Modify | `backend/.../service/PartService.java:183-187` | Tighten part creation to `draft` only, remove `getOrCreate` call |
| Modify | `backend/.../service/ReturnOrderService.java:199-210` | Add parts validation + batch analysis order creation on submit |
| Create | `backend/.../service/ReturnOrderServiceSubmitTest.java` | Unit tests for submit validation and analysis order creation |
| Modify | `frontend/src/views/return-orders/OrderDetail.vue:10,154-157` | Rename submit button to "结束录入", tighten `canAddPart` to `draft` only |
| Modify | `frontend/src/i18n/locales/zh-CN.ts` | Add i18n keys for "结束录入" |
| Modify | `frontend/src/i18n/locales/en-US.ts` | Add i18n keys for "End Entry" |

---

### Task 1: Backend — Tighten PartService to draft-only + remove analysis order auto-creation

**Files:**
- Modify: `backend/src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/service/PartService.java:183-187,253`
- Test: `backend/src/test/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/service/AnalysisOrderServiceGetOrCreateTest.java`

- [ ] **Step 1: Change PartService.create() validation to draft-only**

In `PartService.java`, change lines 183-187 from:

```java
    String orderStatus = returnOrder.getStatus();
    // Only draft and submitted status can add parts
    if (!STATUS_DRAFT.equals(orderStatus) && !STATUS_SUBMITTED.equals(orderStatus)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Parts can only be added to return orders in 'draft' or 'submitted' status. Current status: "
                        + orderStatus);
    }
```

to:

```java
    String orderStatus = returnOrder.getStatus();
    if (!STATUS_DRAFT.equals(orderStatus)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Parts can only be added to return orders in 'draft' status. Current status: "
                        + orderStatus);
    }
```

- [ ] **Step 2: Remove analysis order auto-creation from PartService.create()**

In `PartService.java`, remove the line after `partRepo.save(part)` (around line 253):

```java
    // 触发分析单自动创建（幂等）
    analysisOrderService.getOrCreate(dto.getOrderId(), dto.getAnalyst());
```

This means analysis orders are no longer created when a part is added.

- [ ] **Step 3: Commit**

```bash
cd backend && git add src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/service/PartService.java
git commit -m "refactor: restrict part creation to draft status, remove auto analysis order creation"
```

---

### Task 2: Backend — Add batch analysis order creation to ReturnOrderService.submit()

**Files:**
- Modify: `backend/src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/service/ReturnOrderService.java:199-210`
- Create: `backend/src/test/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/service/ReturnOrderServiceSubmitTest.java`

- [ ] **Step 1: Write the test for submit validation (no parts)**

Create `backend/src/test/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/service/ReturnOrderServiceSubmitTest.java`:

```java
package com.bosch.rbcc.aftermarketpartsmanagementsystem.service;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.ReturnOrder;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.AnalysisOrderRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.CustomerRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.PartRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.ReturnOrderRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.config.ExportProperties;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.service.excel.ReturnOrderExcelHandler;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReturnOrderServiceSubmitTest {

    @Mock private ReturnOrderRepository orderRepo;
    @Mock private PartRepository partRepo;
    @Mock private CustomerRepository customerRepo;
    @Mock private AnalysisOrderRepository analysisOrderRepo;
    @Mock private ReturnOrderExcelHandler excelHandler;
    @Mock private ExportProperties exportProperties;
    @Mock private EntityManager entityManager;

    @InjectMocks
    private ReturnOrderService service;

    @Test
    void submit_emptyOrder_throwsBadRequest() {
        ReturnOrder order = ReturnOrder.builder().id("order-1").status("draft").build();
        when(orderRepo.findById("order-1")).thenReturn(Optional.of(order));
        when(partRepo.findByOrderId("order-1")).thenReturn(Collections.emptyList());

        assertThrows(ResponseStatusException.class, () -> service.submit("order-1"));
    }

    @Test
    void submit_notDraft_throwsBadRequest() {
        ReturnOrder order = ReturnOrder.builder().id("order-1").status("submitted").build();
        when(orderRepo.findById("order-1")).thenReturn(Optional.of(order));

        assertThrows(ResponseStatusException.class, () -> service.submit("order-1"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails (submit_emptyOrder_throwsBadRequest)**

Run:
```bash
cd backend && C:\Users\XEF1CNG\.m2\wrapper\dists\apache-maven-3.9.12-bin\5nmfsn99br87k5d4ajlekdq10k\apache-maven-3.9.12\bin\mvn test -pl . -Dtest=ReturnOrderServiceSubmitTest -Dsurefire.useFile=false
```
Expected: FAIL — current `submit()` does not check for parts

- [ ] **Step 3: Modify ReturnOrderService.submit() to validate parts and batch-create analysis orders**

In `ReturnOrderService.java`, replace the `submit()` method (lines 199-210) with:

```java
@Transactional
public ReturnOrderDTO submit(String id) {
    ReturnOrder order = orderRepo.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + id));
    if (!STATUS_DRAFT.equals(order.getStatus())) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order is not in draft status");
    }

    // Validate: must have at least one part
    List<Part> parts = partRepo.findByOrderId(id);
    if (parts.isEmpty()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot submit return order without parts");
    }

    order.setOrderNumber(generateOrderNumber());
    order.setStatus(STATUS_SUBMITTED);
    orderRepo.save(order);

    // Batch-create analysis orders grouped by analyst
    // Uses analysisOrderRepo directly to avoid circular dependency with AnalysisOrderService
    java.util.Set<String> analysts = parts.stream()
            .map(Part::getAnalyst)
            .filter(a -> a != null && !a.isBlank())
            .collect(java.util.stream.Collectors.toSet());
    for (String analyst : analysts) {
        if (analysisOrderRepo.findByOrderIdAndAnalyst(id, analyst).isEmpty()) {
            String initialStatus = ComplaintTypeConstants.isZeroKm(order.getComplaintType())
                    ? "analysis_completed"
                    : "pending_sampling";
            com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.AnalysisOrder ao =
                    com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.AnalysisOrder.builder()
                            .id(java.util.UUID.randomUUID().toString())
                            .orderId(id)
                            .analyst(analyst)
                            .status(initialStatus)
                            .statusChangedAt(java.time.LocalDateTime.now())
                            .build();
            analysisOrderRepo.save(ao);
        }
    }

    return toDTO(order);
}
```

Add the import at the top of the file:
```java
import com.bosch.rbcc.aftermarketpartsmanagementsystem.constant.ComplaintTypeConstants;
```

- [ ] **Step 4: Run tests to verify they pass**

Run:
```bash
cd backend && C:\Users\XEF1CNG\.m2\wrapper\dists\apache-maven-3.9.12-bin\5nmfsn99br87k5d4ajlekdq10k\apache-maven-3.9.12\bin\mvn test -pl . -Dtest=ReturnOrderServiceSubmitTest -Dsurefire.useFile=false
```
Expected: PASS

- [ ] **Step 5: Run existing tests to verify no regression**

Run:
```bash
cd backend && C:\Users\XEF1CNG\.m2\wrapper\dists\apache-maven-3.9.12-bin\5nmfsn99br87k5d4ajlekdq10k\apache-maven-3.9.12\bin\mvn test -pl . -Dsurefire.useFile=false
```
Expected: All existing tests pass

- [ ] **Step 6: Commit**

```bash
cd backend && git add src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/service/ReturnOrderService.java src/test/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/service/ReturnOrderServiceSubmitTest.java
git commit -m "feat: validate parts on submit and batch-create analysis orders"
```

---

### Task 3: Frontend — Change submit button to "结束录入" and restrict canAddPart

**Files:**
- Modify: `frontend/src/views/return-orders/OrderDetail.vue:10,154-157,228-251`
- Modify: `frontend/src/i18n/locales/zh-CN.ts`
- Modify: `frontend/src/i18n/locales/en-US.ts`

- [ ] **Step 1: Add i18n keys**

In `frontend/src/i18n/locales/zh-CN.ts`, in the `common` section (around line 9), add:

```typescript
    endEntry: '结束录入',
```

In the `message` section, change `submitConfirmWarning` from:

```typescript
    submitConfirmWarning: '提交后无法修改，请确认！',
```

to:

```typescript
    endEntryConfirmWarning: '结束录入后将无法添加或删除零件，并自动创建分析单。请确认！',
```

In `frontend/src/i18n/locales/en-US.ts`, in the `common` section (around line 9), add:

```typescript
    endEntry: 'End Entry',
```

In the `message` section, change `submitConfirmWarning` from:

```typescript
    submitConfirmWarning: 'After submission, modifications are not allowed. Please confirm!',
```

to:

```typescript
    endEntryConfirmWarning: 'After ending entry, you cannot add or delete parts, and analysis orders will be created automatically. Please confirm!',
```

**Note:** Search the entire codebase for `submitConfirmWarning` usage. If other components still use it, keep the old key and add the new one alongside. If only `OrderDetail.vue` uses it, the rename is safe.

- [ ] **Step 2: Update OrderDetail.vue button text**

In `frontend/src/views/return-orders/OrderDetail.vue`, change line 10 from:

```html
<a-button v-if="order?.status === 'draft'" type="primary" @click="handleSubmit">{{ t('common.submit') }}</a-button>
```

to:

```html
<a-button v-if="order?.status === 'draft'" type="primary" @click="handleEndEntry">{{ t('common.endEntry') }}</a-button>
```

- [ ] **Step 3: Update canAddPart to draft-only**

In `OrderDetail.vue`, change lines 154-157 from:

```typescript
const canAddPart = computed(() => {
  if (!order.value) return false
  return order.value.status === 'draft' || order.value.status === 'submitted'
})
```

to:

```typescript
const canAddPart = computed(() => {
  if (!order.value) return false
  return order.value.status === 'draft'
})
```

- [ ] **Step 4: Rename handleSubmit to handleEndEntry and update confirmation text**

In `OrderDetail.vue`, change lines 228-251 from:

```typescript
const confirmSubmit = () => {
  return new Promise<boolean>((resolve) => {
    Modal.confirm({
      title: t('common.tip'),
      content: t('message.submitConfirmWarning'),
      okText: t('common.confirm'),
      cancelText: t('common.cancel'),
      onOk: () => resolve(true),
      onCancel: () => resolve(false),
    })
  })
}

const handleSubmit = async () => {
  const confirmed = await confirmSubmit()
  if (!confirmed) return

  try {
    order.value = await returnOrderApi.submit(orderId.value)
    message.success(t('message.submitSuccess'))
  } catch {
    message.error(t('message.submitSuccess'))
  }
}
```

to:

```typescript
const confirmEndEntry = () => {
  return new Promise<boolean>((resolve) => {
    Modal.confirm({
      title: t('common.tip'),
      content: t('message.endEntryConfirmWarning'),
      okText: t('common.confirm'),
      cancelText: t('common.cancel'),
      onOk: () => resolve(true),
      onCancel: () => resolve(false),
    })
  })
}

const handleEndEntry = async () => {
  const confirmed = await confirmEndEntry()
  if (!confirmed) return

  try {
    order.value = await returnOrderApi.submit(orderId.value)
    message.success(t('message.endEntrySuccess'))
  } catch {
    message.error(t('message.submitFailed'))
  }
}
```

Add the i18n keys for success message:
- `zh-CN.ts` message section: `endEntrySuccess: '结束录入成功，已创建分析单',`
- `en-US.ts` message section: `endEntrySuccess: 'Entry ended successfully, analysis orders created',`

- [ ] **Step 5: Verify no other references to `submitConfirmWarning` or `handleSubmit` in OrderDetail.vue**

Search the codebase for `submitConfirmWarning` to confirm no other components reference it. If found, keep the old key and add the new one.

- [ ] **Step 6: Run dev server and verify**

Run:
```bash
cd frontend && npm run dev
```

Manually verify:
1. Create a new return order (draft) — "结束录入" button should be visible
2. Click "结束录入" — confirmation dialog should show the new message
3. After confirm — status changes to submitted, add part button disappears
4. Submitted order — no add/edit/delete part operations visible

- [ ] **Step 7: Commit**

```bash
cd frontend && git add src/views/return-orders/OrderDetail.vue src/i18n/locales/zh-CN.ts src/i18n/locales/en-US.ts
git commit -m "feat: rename submit to end-entry, restrict part creation to draft status only"
```

---

### Task 4: Documentation — Update design doc

**Files:**
- Modify: `doc/01-设计文档/开发设计文档.md`

- [ ] **Step 1: Update documentation**

Update the design document to reflect the new phase gate workflow:
- Document that `draft` → `submitted` is now a phase gate
- Document that analysis orders are batch-created on submit, not on part creation
- Document that parts can only be added in `draft` status

- [ ] **Step 2: Commit**

```bash
cd doc && git add 01-设计文档/开发设计文档.md
git commit -m "docs: update design doc with phase gate workflow"
```
