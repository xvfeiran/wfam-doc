# 审批超期提醒修复（用例 435/436/437）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 `sendApprovalReminderNotifications` 真正基于 AnalysisOrder 的 `pending_approval` 状态发出审批超期提醒，使 435/436/437 可测可通过。

**Architecture:** 查询源从 Part 改为 AnalysisOrder；`APMS_NOTIFICATION_LOG` 新增 `ANALYSIS_ORDER_ID` 列并把 `PART_ID` 放宽为可空，审批提醒按批次维度去重；收件人仅 QMC Leader；邮件显示退货单号 + 批次 analyst + 等待天数。

**Tech Stack:** Java 21, Spring Boot, Spring Data JPA (derived queries), Flyway (Oracle), JUnit 5 + Mockito。

## Global Constraints

- JDK 21 路径：`C:\Users\XEF1CNG\.jdks\corretto-21.0.5`；Maven：`C:\Users\XEF1CNG\.m2\wrapper\dists\apache-maven-3.9.12\6068d197\bin\mvn.cmd`
- 编译命令：`mvn -f C:\Users\XEF1CNG\code\wfam\backend\pom.xml -DskipTests compile`（设好 `JAVA_HOME` 后）
- Oracle SQL，迁移文件放 `backend/src/main/resources/db/migration/`，命名 `V<n>__<desc>.sql`，下一个版本号 **V53**
- 本地 `application-local.yml` 中 `flyway.enabled=false`，迁移需**手动对本地 Oracle 执行**
- 三个独立 git 仓库：backend 代码提交进 `backend/`，文档提交进 `doc/`
- 业务变更须同步更新 `doc/01-设计文档/开发设计文档.md` 与 `doc/02-工作进度/测试文档.md`（CLAUDE.md 硬性要求）
- 测试用例状态非经人类明确指令不得改为「通过」（CLAUDE.md §3）

---

## File Structure

- `backend/src/main/resources/db/migration/V53__add_analysis_order_id_to_notification_log.sql` — 新建，schema 迁移
- `backend/src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/entity/NotificationLog.java` — 加 `analysisOrderId` 字段，`partId` 放宽可空
- `backend/src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/repository/NotificationLogRepository.java` — 加批次维度去重查询
- `backend/src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/repository/AnalysisOrderRepository.java` — 加按状态+时间查询
- `backend/src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/service/NotificationService.java` — DI 两个 repo；重构 `sendAndLog` 签名；重写 `sendApprovalReminderNotifications`；加 `shouldSkipOrder`、`buildApprovalReminderEmail(AnalysisOrder,…)`
- `backend/src/test/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/service/NotificationServiceApprovalReminderTest.java` — 新建，Mockito 单元测试
- `doc/01-设计文档/开发设计文档.md` — 表结构 + 通知章节 + v5.3 变更记录
- `doc/02-工作进度/测试文档.md` — 435/436/437 备注
- `doc/02-工作进度/7.4.2-定时通知-测试操作手册.md` — §4 改"已修复"，新增 435/436/437 操作章节

---

## Task 1: Flyway 迁移 V53（schema）

**Files:**
- Create: `backend/src/main/resources/db/migration/V53__add_analysis_order_id_to_notification_log.sql`

- [ ] **Step 1: 写迁移 SQL（幂等，仿 V52 风格）**

```sql
-- V53: 为 APMS_NOTIFICATION_LOG 新增 ANALYSIS_ORDER_ID，并放宽 PART_ID 为可空
-- 审批超期提醒(APPROVAL_REMINDER)按分析批次维度记录与去重，该类记录无 part，PART_ID 为 NULL。

DECLARE
  v_count NUMBER;
BEGIN
  SELECT COUNT(*) INTO v_count
  FROM user_tab_columns
  WHERE table_name = 'APMS_NOTIFICATION_LOG'
    AND column_name = 'ANALYSIS_ORDER_ID';

  IF v_count = 0 THEN
    EXECUTE IMMEDIATE 'ALTER TABLE APMS_NOTIFICATION_LOG ADD ANALYSIS_ORDER_ID VARCHAR2(36)';
    DBMS_OUTPUT.PUT_LINE('已添加 ANALYSIS_ORDER_ID 列');
  ELSE
    DBMS_OUTPUT.PUT_LINE('ANALYSIS_ORDER_ID 列已存在，跳过');
  END IF;
END;
/

-- PART_ID 放宽为可空（APPROVAL_REMINDER 行无 part）
ALTER TABLE APMS_NOTIFICATION_LOG MODIFY PART_ID NULL;

COMMENT ON COLUMN APMS_NOTIFICATION_LOG.ANALYSIS_ORDER_ID IS '关联分析批次ID，仅 APPROVAL_REMINDER 使用；其他类型为 NULL';
COMMENT ON COLUMN APMS_NOTIFICATION_LOG.PART_ID IS '关联售后件ID，APPROVAL_REMINDER 时为 NULL';
```

- [ ] **Step 2: 手动对本地 Oracle 执行该 SQL**

本地 `flyway.enabled=false`，迁移不会自动跑。用 SQL 客户端连 `application-local.yml` 里的库（`cngorarac01.apac.bosch.com:38000/aepqual_app.apac.bosch.com`，用户 `SUPER_LINE_LEADER`）整段执行。

- [ ] **Step 3: 验证列已存在**

```sql
SELECT COLUMN_NAME, NULLABLE
FROM   user_tab_columns
WHERE  TABLE_NAME = 'APMS_NOTIFICATION_LOG'
  AND  COLUMN_NAME IN ('PART_ID','ANALYSIS_ORDER_ID');
```

预期：`PART_ID` NULLABLE='Y'；`ANALYSIS_ORDER_ID` NULLABLE='Y'。

- [ ] **Step 4: 提交（backend 仓库）**

```bash
cd C:/Users/XEF1CNG/code/wfam/backend
git add src/main/resources/db/migration/V53__add_analysis_order_id_to_notification_log.sql
git commit -m "feat(db): V53 add ANALYSIS_ORDER_ID to notification log, relax PART_ID nullable"
```

---

## Task 2: 实体字段 + Repository 派生查询

**Files:**
- Modify: `backend/src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/entity/NotificationLog.java`
- Modify: `backend/src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/repository/NotificationLogRepository.java`
- Modify: `backend/src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/repository/AnalysisOrderRepository.java`

**Interfaces:**
- Produces: `NotificationLog.analysisOrderId` 字段；`NotificationLogRepository.existsByAnalysisOrderIdAndNotificationTypeAndStatusAndSentAtAfter(...)`；`AnalysisOrderRepository.findByStatusAndStatusChangedAtLessThanEqual(...)`（供 Task 3/4 使用）

- [ ] **Step 1: NotificationLog 加字段、放宽 partId**

在 `NotificationLog.java` 的 `private String partId;` 之上 `@Column(name = "PART_ID", length = 36, nullable = false)` 改为 `nullable = true`；并在 `ccRecipients` 字段后新增：

```java
    @Column(name = "ANALYSIS_ORDER_ID", length = 36)
    private String analysisOrderId;
```

即把现有：
```java
    @Column(name = "PART_ID", length = 36, nullable = false)
    private String partId;
```
改为：
```java
    @Column(name = "PART_ID", length = 36)
    private String partId;
```

并在 `ccRecipients` 字段之后、`status` 字段之前插入新字段。

- [ ] **Step 2: NotificationLogRepository 加批次维度方法**

在 `existsByPartIdAndNotificationTypeAndStatusAndSentAtAfter` 之后追加：

```java
    /**
     * 批次维度去重：仅 APPROVAL_REMINDER 使用。
     * 只统计 STATUS='SENT' 的记录，FAILED 不计，确保失败的下轮重试。
     */
    boolean existsByAnalysisOrderIdAndNotificationTypeAndStatusAndSentAtAfter(
            String analysisOrderId, String notificationType, String status, LocalDateTime after);
```

- [ ] **Step 3: AnalysisOrderRepository 加查询方法**

在 `AnalysisOrderRepository` 接口体内（任意现有方法之后）追加：

```java
    List<AnalysisOrder> findByStatusAndStatusChangedAtLessThanEqual(String status, LocalDateTime threshold);
```

确认文件顶部已 import `java.time.LocalDateTime` 与 `java.util.List`（已存在则不重复加）。

- [ ] **Step 4: 编译验证**

Run（PowerShell，设好 JAVA_HOME）：
```powershell
$env:JAVA_HOME="C:\Users\XEF1CNG\.jdks\corretto-21.0.5"
& "C:\Users\XEF1CNG\.m2\wrapper\dists\apache-maven-3.9.12\6068d197\bin\mvn.cmd" -f C:\Users\XEF1CNG\code\wfam\backend\pom.xml -DskipTests compile
```
Expected: `BUILD SUCCESS`

- [ ] **Step 5: 提交**

```bash
cd C:/Users/XEF1CNG/code/wfam/backend
git add src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/entity/NotificationLog.java \
        src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/repository/NotificationLogRepository.java \
        src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/repository/AnalysisOrderRepository.java
git commit -m "feat(notification): add analysisOrderId field + batch-level dedup query"
```

---

## Task 3: NotificationService 预备改动（DI + sendAndLog 签名重构）

> 本任务不改任何行为，仅做让 Task 4 测试可编译的机械重构。编译即验收。

**Files:**
- Modify: `backend/src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/service/NotificationService.java`

**Interfaces:**
- Consumes: Task 2 的 repo 方法
- Produces: `sendAndLog(String partId, String analysisOrderId, String type, …)` 新签名（Task 4 调用）

- [ ] **Step 1: 注入两个 Repository**

在 `NotificationService` 字段区（现有 `private final UserEmailService userEmailService;` 之后）追加：

```java
    private final AnalysisOrderRepository analysisOrderRepository;
    private final ReturnOrderRepository returnOrderRepository;
```

并在文件顶部 import 区追加（若已存在则跳过）：

```java
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.AnalysisOrder;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.ReturnOrder;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.AnalysisOrderRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.ReturnOrderRepository;
```

类上保留 Lombok `@RequiredArgsConstructor`（已存在），新 final 字段会自动加入构造器。

- [ ] **Step 2: 重构 sendAndLog 签名**

把现有：
```java
    private void sendAndLog(String partId, String type, List<String> recipients, List<String> ccList,
                            String subject, String content) {
        String recipientsStr = String.join(";", recipients);
        String ccStr = ccList.isEmpty() ? null : String.join(";", ccList);

        // 同步发送：按真实结果落库状态。任一收件人/CC 失败 → STATUS=FAILED，
        // shouldSkip 只认 SENT，因此失败记录不进 dedup，下一轮 cron 自动重试。
        List<String> errors = new ArrayList<>();
        for (String to : recipients) {
            emailService.sendHtmlEmailSync(to, subject, content, errors);
        }
        for (String cc : ccList) {
            emailService.sendHtmlEmailSync(cc, subject, content, errors);
        }

        boolean allSent = errors.isEmpty();
        String status = allSent ? STATUS_SENT : STATUS_FAILED;
        String errorMessage = null;
        if (!allSent) {
            errorMessage = String.join("; ", errors);
            if (errorMessage.length() > 500) {
                errorMessage = errorMessage.substring(0, 497) + "...";
            }
        }

        try {
            logRepo.save(NotificationLog.builder()
                .id(UUID.randomUUID().toString())
                .partId(partId)
                .notificationType(type)
                .recipients(recipientsStr)
                .ccRecipients(ccStr)
                .status(status)
                .sentAt(LocalDateTime.now())
                .errorMessage(errorMessage)
                .build());
        } catch (Exception e) {
            log.warn("Failed to log notification: {}", e.getMessage());
        }
        log.info("Notification dispatched: type={}, partId={}, recipients={}, status={}",
            type, partId, recipientsStr, status);
    }
```

改为（新增 `analysisOrderId` 参数；落库写入两列；日志补 analysisOrderId）：

```java
    private void sendAndLog(String partId, String analysisOrderId, String type,
                            List<String> recipients, List<String> ccList,
                            String subject, String content) {
        String recipientsStr = String.join(";", recipients);
        String ccStr = ccList.isEmpty() ? null : String.join(";", ccList);

        // 同步发送：按真实结果落库状态。任一收件人/CC 失败 → STATUS=FAILED，
        // shouldSkip 只认 SENT，因此失败记录不进 dedup，下一轮 cron 自动重试。
        List<String> errors = new ArrayList<>();
        for (String to : recipients) {
            emailService.sendHtmlEmailSync(to, subject, content, errors);
        }
        for (String cc : ccList) {
            emailService.sendHtmlEmailSync(cc, subject, content, errors);
        }

        boolean allSent = errors.isEmpty();
        String status = allSent ? STATUS_SENT : STATUS_FAILED;
        String errorMessage = null;
        if (!allSent) {
            errorMessage = String.join("; ", errors);
            if (errorMessage.length() > 500) {
                errorMessage = errorMessage.substring(0, 497) + "...";
            }
        }

        try {
            logRepo.save(NotificationLog.builder()
                .id(UUID.randomUUID().toString())
                .partId(partId)
                .analysisOrderId(analysisOrderId)
                .notificationType(type)
                .recipients(recipientsStr)
                .ccRecipients(ccStr)
                .status(status)
                .sentAt(LocalDateTime.now())
                .errorMessage(errorMessage)
                .build());
        } catch (Exception e) {
            log.warn("Failed to log notification: {}", e.getMessage());
        }
        log.info("Notification dispatched: type={}, partId={}, analysisOrderId={}, recipients={}, status={}",
            type, partId, analysisOrderId, recipientsStr, status);
    }
```

- [ ] **Step 3: 更新全部 5 个现有调用点为新签名（补 null analysisOrderId）**

逐一替换：

`sendAndLog(partId, TYPE_RESPONSIBILITY, recipients, ccList, subject, content);`
→ `sendAndLog(partId, null, TYPE_RESPONSIBILITY, recipients, ccList, subject, content);`

`sendAndLog(partId, TYPE_ZERO_KM, recipients, List.of(), subject, content);`
→ `sendAndLog(partId, null, TYPE_ZERO_KM, recipients, List.of(), subject, content);`

`sendAndLog(part.getId(), TYPE_WARNING, recipients, List.of(), subject, content);`
→ `sendAndLog(part.getId(), null, TYPE_WARNING, recipients, List.of(), subject, content);`

`sendAndLog(part.getId(), TYPE_OVERDUE, recipients, ccList, subject, content);`
→ `sendAndLog(part.getId(), null, TYPE_OVERDUE, recipients, ccList, subject, content);`

`sendAndLog(part.getId(), TYPE_APPROVAL_REMINDER, recipients, List.of(), subject, content);`
→ `sendAndLog(null, part.getId(), TYPE_APPROVAL_REMINDER, recipients, List.of(), subject, content);`

> 注意最后一行（approval）：临时仍走 part 分支（bug 未修），但签名先对齐为 `(null partId, part.getId() as analysisOrderId, …)` 以便编译。Task 4 会整段重写该方法体，此行将被覆盖。

- [ ] **Step 4: 编译验证**

Run（同 Task 2 Step 4 命令）。Expected: `BUILD SUCCESS`。

- [ ] **Step 5: 提交**

```bash
cd C:/Users/XEF1CNG/code/wfam/backend
git add src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/service/NotificationService.java
git commit -m "refactor(notification): inject order repos, sendAndLog accepts analysisOrderId"
```

---

## Task 4: TDD 重写 sendApprovalReminderNotifications

**Files:**
- Create: `backend/src/test/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/service/NotificationServiceApprovalReminderTest.java`
- Modify: `backend/src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/service/NotificationService.java`

**Interfaces:**
- Consumes: Task 2/3 的 repo 方法、sendAndLog 新签名、AnalysisOrder/ReturnOrder 实体
- Produces: 真正生效的审批超期提醒

- [ ] **Step 1: 写失败的测试**

新建 `NotificationServiceApprovalReminderTest.java`：

```java
package com.bosch.rbcc.aftermarketpartsmanagementsystem.service;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.config.NotificationProperties;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.AnalysisOrder;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.NotificationLog;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.ReturnOrder;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.AnalysisOrderRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.NotificationLogRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.PartRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.ReturnOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationServiceApprovalReminderTest {

    private EmailService emailService;
    private NotificationProperties props;
    private NotificationLogRepository logRepo;
    private PartRepository partRepo;
    private UserEmailService userEmailService;
    private AnalysisOrderRepository analysisOrderRepo;
    private ReturnOrderRepository returnOrderRepo;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        emailService = mock(EmailService.class);
        props = new NotificationProperties();
        props.getApproval().setOverdueDays(0);          // 任何过去时间都满足
        props.getFrequency().setApprovalReminder(3);
        logRepo = mock(NotificationLogRepository.class);
        partRepo = mock(PartRepository.class);          // 默认返回空 List，预警/超期不触发
        userEmailService = mock(UserEmailService.class);
        analysisOrderRepo = mock(AnalysisOrderRepository.class);
        returnOrderRepo = mock(ReturnOrderRepository.class);

        service = new NotificationService(emailService, props, logRepo, partRepo,
                userEmailService, analysisOrderRepo, returnOrderRepo);

        when(emailService.sendHtmlEmailSync(anyString(), anyString(), anyString(), anyList()))
                .thenReturn(true);
    }

    /** 435：pending_approval 批次超期 → 发提醒给 QMC Leader，按 analysisOrderId 落库 */
    @Test
    void approvalReminder_pendingOrderOverdue_sendsToQmcLeaderAndLogsByOrderId() {
        AnalysisOrder order = AnalysisOrder.builder()
                .id("order-1").orderId("ro-1").analyst("analyst1")
                .status("pending_approval")
                .statusChangedAt(LocalDateTime.now().minusDays(1))
                .build();
        when(analysisOrderRepo.findByStatusAndStatusChangedAtLessThanEqual(
                eq("pending_approval"), any(LocalDateTime.class)))
                .thenReturn(List.of(order));
        when(logRepo.existsByAnalysisOrderIdAndNotificationTypeAndStatusAndSentAtAfter(
                eq("order-1"), eq("APPROVAL_REMINDER"), eq("SENT"), any(LocalDateTime.class)))
                .thenReturn(false);
        when(userEmailService.getQmcLeaderEmails()).thenReturn(List.of("leader@cn.bosch.com"));
        when(returnOrderRepo.findById("ro-1")).thenReturn(
                Optional.of(ReturnOrder.builder().id("ro-1").orderNumber("RO-001").build()));

        service.scheduledNotificationCheck();

        verify(emailService).sendHtmlEmailSync(eq("leader@cn.bosch.com"), contains("RO-001"), anyString(), anyList());

        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepo).save(captor.capture());
        NotificationLog saved = captor.getValue();
        assertEquals("APPROVAL_REMINDER", saved.getNotificationType());
        assertEquals("order-1", saved.getAnalysisOrderId());
        assertNull(saved.getPartId());
        assertEquals("SENT", saved.getStatus());
        assertEquals("leader@cn.bosch.com", saved.getRecipients());
    }

    /** 436 前置：3 天频率窗口内已发过 → 跳过，不再发 */
    @Test
    void approvalReminder_withinFrequencyWindow_skips() {
        AnalysisOrder order = AnalysisOrder.builder()
                .id("order-2").orderId("ro-2").analyst("analyst2")
                .status("pending_approval")
                .statusChangedAt(LocalDateTime.now().minusDays(1))
                .build();
        when(analysisOrderRepo.findByStatusAndStatusChangedAtLessThanEqual(
                eq("pending_approval"), any(LocalDateTime.class)))
                .thenReturn(List.of(order));
        when(logRepo.existsByAnalysisOrderIdAndNotificationTypeAndStatusAndSentAtAfter(
                eq("order-2"), eq("APPROVAL_REMINDER"), eq("SENT"), any(LocalDateTime.class)))
                .thenReturn(true);

        service.scheduledNotificationCheck();

        verify(emailService, never()).sendHtmlEmailSync(anyString(), anyString(), anyString(), anyList());
        verify(logRepo, never()).save(any());
    }

    /** 437：批次已离开 pending_approval → 不在候选集 → 不发 */
    @Test
    void approvalReminder_orderNotPending_notACandidate() {
        when(analysisOrderRepo.findByStatusAndStatusChangedAtLessThanEqual(
                eq("pending_approval"), any(LocalDateTime.class)))
                .thenReturn(List.of());  // 已 approved，候选集为空

        service.scheduledNotificationCheck();

        verify(emailService, never()).sendHtmlEmailSync(anyString(), anyString(), anyString(), anyList());
        verify(logRepo, never()).save(any());
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

Run:
```powershell
$env:JAVA_HOME="C:\Users\XEF1CNG\.jdks\corretto-21.0.5"
& "C:\Users\XEF1CNG\.m2\wrapper\dists\apache-maven-3.9.12\6068d197\bin\mvn.cmd" -f C:\Users\XEF1CNG\code\wfam\backend\pom.xml -Dtest=NotificationServiceApprovalReminderTest test
```
Expected: 第一个测试 FAIL（当前 `sendApprovalReminderNotifications` 查 partRepo 返回空，不发邮件，`verify(emailService).sendHtmlEmailSync` 失败）。

- [ ] **Step 3: 重写 sendApprovalReminderNotifications + 新增 shouldSkipOrder + buildApprovalReminderEmail**

把 `NotificationService.java` 中现有 `sendApprovalReminderNotifications()` 整段（从 `private void sendApprovalReminderNotifications() {` 到对应 `}`）替换为：

```java
    private void sendApprovalReminderNotifications() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(props.getApproval().getOverdueDays());
        List<AnalysisOrder> orders = analysisOrderRepository.findByStatusAndStatusChangedAtLessThanEqual(
            STATUS_PENDING_APPROVAL, threshold);

        log.info("APPROVAL scan: candidateCount={}, threshold={}", orders.size(), threshold);
        for (AnalysisOrder order : orders) {
            log.info("APPROVAL check: analysisOrderId={}, orderId={}, analyst={}, statusChangedAt={}",
                order.getId(), order.getOrderId(), order.getAnalyst(), order.getStatusChangedAt());
            if (shouldSkipOrder(order.getId(), TYPE_APPROVAL_REMINDER,
                                props.getFrequency().getApprovalReminder())) {
                log.info("APPROVAL skip (within frequency window {}d): analysisOrderId={}",
                    props.getFrequency().getApprovalReminder(), order.getId());
                continue;
            }

            List<String> recipients = new ArrayList<>();
            addQmcLeaders(recipients);
            if (recipients.isEmpty()) {
                log.warn("APPROVAL skip (no QMC leader email): analysisOrderId={}", order.getId());
                continue;
            }

            long daysPending = Duration.between(order.getStatusChangedAt(), LocalDateTime.now()).toDays();
            String orderNumber = returnOrderRepository.findById(order.getOrderId())
                .map(ReturnOrder::getOrderNumber)
                .orElse(order.getOrderId());
            String subject = String.format("[WFAM] 审批超期提醒 - 退货单 %s - 审批已等待 %d 天",
                orderNumber, daysPending);
            String content = buildApprovalReminderEmail(order, orderNumber, daysPending);

            sendAndLog(null, order.getId(), TYPE_APPROVAL_REMINDER, recipients, List.of(), subject, content);
        }
    }

    private boolean shouldSkipOrder(String analysisOrderId, String type, int frequencyDays) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(frequencyDays);
        // 只认 SENT：FAILED 记录不构成"已发送"，下一轮 cron 会重试。
        return logRepo.existsByAnalysisOrderIdAndNotificationTypeAndStatusAndSentAtAfter(
            analysisOrderId, type, STATUS_SENT, cutoff);
    }
```

并把现有 `buildApprovalReminderEmail(Part part, long days)` 整段替换为接受 `AnalysisOrder` 的版本：

```java
    private String buildApprovalReminderEmail(AnalysisOrder order, String orderNumber, long days) {
        return String.format("""
            <h3>审批超期提醒</h3>
            <p>退货单 <strong>%s</strong>（分析师 %s）的精分析报告审批已等待 <strong>%d</strong> 天。</p>
            <table border="1" cellpadding="5" cellspacing="0">
            <tr><td>退货单号</td><td>%s</td></tr>
            <tr><td>分析师</td><td>%s</td></tr>
            <tr><td>等待审批天数</td><td>%d</td></tr>
            </table>
            <p>请尽快完成审批。</p>
            """,
            orderNumber, order.getAnalyst(),
            orderNumber, order.getAnalyst(), days);
    }
```

- [ ] **Step 4: 运行测试，确认全绿**

Run（同 Step 2 命令）。Expected: 3 个测试全部 PASS。

- [ ] **Step 5: 全量编译 + 已有测试不回归**

Run:
```powershell
& "C:\Users\XEF1CNG\.m2\wrapper\dists\apache-maven-3.9.12\6068d197\bin\mvn.cmd" -f C:\Users\XEF1CNG\code\wfam\backend\pom.xml test
```
Expected: `BUILD SUCCESS`（含既有 `AnalysisOrderServiceGetOrCreateTest` 等）。

- [ ] **Step 6: 提交**

```bash
cd C:/Users/XEF1CNG/code/wfam/backend
git add src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/service/NotificationService.java \
        src/test/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/service/NotificationServiceApprovalReminderTest.java
git commit -m "fix(notification): approval reminder queries AnalysisOrder, dedups by order id"
```

---

## Task 5: 本地端到端验证 435/436/437 + 文档同步

**Files:**
- Modify: `doc/01-设计文档/开发设计文档.md`
- Modify: `doc/02-工作进度/测试文档.md`
- Modify: `doc/02-工作进度/7.4.2-定时通知-测试操作手册.md`

- [ ] **Step 1: 重启后端**

```powershell
cd C:\Users\XEF1CNG\code\wfam\backend
.\mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

- [ ] **Step 2: 用例 435 — 触发审批提醒**

构造一条 pending_approval 超期批次（先查一个可用 ID）：
```sql
SELECT ID, ORDER_ID, ANALYST, STATUS FROM APMS_ANALYSIS_ORDER WHERE ROWNUM <= 5;
```
挑一个 `<AO_ID>`，改：
```sql
UPDATE APMS_ANALYSIS_ORDER
SET    STATUS = 'pending_approval',
       STATUS_CHANGED_AT = SYSDATE - 1
WHERE  ID = '<AO_ID>';
DELETE FROM APMS_NOTIFICATION_LOG WHERE ANALYSIS_ORDER_ID = '<AO_ID>';
COMMIT;
```
等 ≤30 秒看日志：`APPROVAL check: analysisOrderId=<AO_ID>...` → `Notification dispatched: type=APPROVAL_REMINDER, analysisOrderId=<AO_ID>, status=SENT`，收到提醒邮件。

- [ ] **Step 3: 用例 436 — 频率窗口内不再发**

不改任何东西，再等一轮 cron。预期：`APPROVAL skip (within frequency window 3d)`，无新邮件、日志表不新增。

- [ ] **Step 4: 用例 437 — 离开 pending_approval 后不再发**

```sql
UPDATE APMS_ANALYSIS_ORDER SET STATUS = 'approved' WHERE ID = '<AO_ID>';
COMMIT;
```
等一轮 cron。预期：该批次不在候选集（`candidateCount` 不含它），无 dispatched。

- [ ] **Step 5: 同步设计文档**

`doc/01-设计文档/开发设计文档.md`：
- ER 图 `APMS_NOTIFICATION_LOG` 块加 `string ANALYSIS_ORDER_ID`；`PART_ID` 标注可空。
- 数据字典 `#### APMS_NOTIFICATION_LOG` 表：`PART_ID` 约束由 `NOT NULL` 改为 `—`；新增一行 `ANALYSIS_ORDER_ID | VARCHAR2(36) | — | 关联分析批次ID，仅 APPROVAL_REMINDER 使用`。
- 2.2.8 审批提醒判定改述为：`APMS_ANALYSIS_ORDER.STATUS = 'pending_approval' 且 STATUS_CHANGED_AT ≤ 阈值`，按批次去重（`ANALYSIS_ORDER_ID + NOTIFICATION_TYPE`）。
- 变更记录表新增：`| v5.3 | 2.2.8, backend | 审批超期提醒修复 | sendApprovalReminderNotifications 改查 AnalysisOrder（原误查 Part），新增 ANALYSIS_ORDER_ID 列、PART_ID 放宽可空，按批次维度去重；收件人仅 QMC Leader | 2026-06-23 |`

- [ ] **Step 6: 同步测试文档 435/436/437 备注**

将 435/436/437 备注列更新为"v5.3 已修复，待实测"（状态保持 `⏳ 待测试`，不标通过）。

- [ ] **Step 7: 同步手册 7.4.2**

`doc/02-工作进度/7.4.2-定时通知-测试操作手册.md`：把 §4"已知缺陷：435/436/437"标题改为"v5.3 已修复"；新增 435/436/437 操作章节（沿用本任务 Step 2-4 的 SQL）。

- [ ] **Step 8: 提交（doc 仓库）**

```bash
cd C:/Users/XEF1CNG/code/wfam/doc
git add 01-设计文档/开发设计文档.md 02-工作进度/测试文档.md 02-工作进度/7.4.2-定时通知-测试操作手册.md
git commit -m "docs: v5.3 approval reminder fix (435/436/437)"
```

- [ ] **Step 9: 收尾 — 还原被测批次状态**

```sql
UPDATE APMS_ANALYSIS_ORDER SET STATUS = '<原 STATUS>', STATUS_CHANGED_AT = '<原值>' WHERE ID = '<AO_ID>';
COMMIT;
```

---

## Self-Review 记录

- **Spec 覆盖**：§2.1 迁移 → Task 1；§2.2 repo → Task 2；§2.3 sendAndLog 重构 → Task 3；§2.3 重写+shouldSkipOrder+邮件 → Task 4；§2.4 文档 → Task 5；§3 测试方法 → Task 5 Step 2-4。无遗漏。
- **占位符**：无 TBD/TODO；所有代码块完整。
- **类型一致**：`sendAndLog(partId, analysisOrderId, type, …)` 在 Task 3 定义、Task 4 调用一致；`existsByAnalysisOrderIdAndNotificationTypeAndStatusAndSentAtAfter`、`findByStatusAndStatusChangedAtLessThanEqual` 在 Task 2 定义、Task 4 测试与实现一致；构造器实参顺序 `(... userEmailService, analysisOrderRepository, returnOrderRepository)` 与 Task 3 字段声明顺序一致。
