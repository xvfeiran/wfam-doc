# 审批超期提醒修复设计（用例 435 / 436 / 437）

> 日期：2026-06-23
> 关联：测试文档 7.4.2 用例 435/436/437；手册 `7.4.2-定时通知-测试操作手册.md` §4 已知缺陷
> 版本：v5.3

## 1. 背景与缺陷

### 1.1 业务意图

售后件精分析报告提交后进入审批，由 QMC Leader 审批。若超过 `custom.notification.approval.overdue-days`（默认 3 天）未审批，定时任务每 `frequency.approval-reminder`（默认 3 天）发一封提醒邮件给 QMC Leader。

- **435**：提交后超 3 天未审批 → 首次提醒；
- **436**：再过 3 天仍未审批 → 再次提醒；
- **437**：审批完成（状态离开 `pending_approval`）→ 不再提醒。

### 1.2 当前缺陷

`NotificationService.sendApprovalReminderNotifications()` 查询的是 **Part 表** 的 `pending_approval` 状态：

```java
List<Part> parts = partRepository.findByStatusAndStatusChangedAtLessThanEqual(
    "pending_approval", threshold);
```

但 `pending_approval` 是 **AnalysisOrder（批次）** 的状态，不是 Part 的状态。Part 报告提交后停在 `analysis_report_submitted`；当批次内所有抽样件都提交，系统把**整个 AnalysisOrder** 置为 `pending_approval`（`AnalysisReportService.java:127`）。

因此上述查询**永远返回空列表**，`APPROVAL scan: candidateCount=0` 恒成立，审批提醒一封都发不出。435/436/437 无法通过验证。

## 2. 方案

查询源从 Part 改为 AnalysisOrder；去重维度从 partId 改为 analysisOrderId（新增列）；收件人仅 QMC Leader；邮件显示退货单号 + 批次 analyst + 等待天数。

### 2.1 数据模型变更

新增 Flyway 迁移 `V53__add_analysis_order_id_to_notification_log.sql`（Oracle，幂等，仿 V52 风格）：

```sql
-- 1) 新增 ANALYSIS_ORDER_ID（nullable，仅 APPROVAL_REMINDER 行使用）
ALTER TABLE APMS_NOTIFICATION_LOG ADD ANALYSIS_ORDER_ID VARCHAR2(36);

-- 2) PART_ID 放宽为可空（APPROVAL_REMINDER 行无 part）
ALTER TABLE APMS_NOTIFICATION_LOG MODIFY PART_ID NULL;

COMMENT ON COLUMN APMS_NOTIFICATION_LOG.ANALYSIS_ORDER_ID IS '关联分析批次ID，仅 APPROVAL_REMINDER 使用；其他类型为 NULL';
COMMENT ON COLUMN APMS_NOTIFICATION_LOG.PART_ID IS '关联售后件ID，APPROVAL_REMINDER 时为 NULL';
```

**注意**：本地环境 `flyway.enabled=false`，需手动执行该 SQL；生产环境由 Flyway 应用。

`NotificationLog` 实体：
- 新增 `@Column(name="ANALYSIS_ORDER_ID", length=36) private String analysisOrderId;`
- `partId` 去掉 `nullable=false`（与 DB 一致）。

> 该表为纯后端日志表，前端无对应 TS interface，不涉及 CLAUDE.md 的 Entity↔Interface 同步约束。

### 2.2 Repository

**`AnalysisOrderRepository`** 新增：

```java
List<AnalysisOrder> findByStatusAndStatusChangedAtLessThanEqual(String status, LocalDateTime threshold);
```

**`NotificationLogRepository`** 新增批次维度去重方法（part 维度方法保留）：

```java
boolean existsByAnalysisOrderIdAndNotificationTypeAndStatusAndSentAtAfter(
        String analysisOrderId, String notificationType, String status, LocalDateTime after);
```

### 2.3 NotificationService 改造

**(a) 重构 `sendAndLog` 签名**，接收 nullable 的 partId 与 analysisOrderId：

```java
private void sendAndLog(String partId, String analysisOrderId, String type,
                        List<String> recipients, List<String> ccList,
                        String subject, String content)
```

落库时按非空者写入（part-level 调用传 `analysisOrderId=null`；approval 调用传 `partId=null`）。同步发送与 SENT/FAILED 判定逻辑不变（沿用 v5.2）。

**(b) 新增 `shouldSkipOrder`**（与 `shouldSkip` 对称，只认 SENT）：

```java
private boolean shouldSkipOrder(String analysisOrderId, String type, int frequencyDays) {
    LocalDateTime cutoff = LocalDateTime.now().minusDays(frequencyDays);
    return logRepo.existsByAnalysisOrderIdAndNotificationTypeAndStatusAndSentAtAfter(
        analysisOrderId, type, STATUS_SENT, cutoff);
}
```

**(c) 重写 `sendApprovalReminderNotifications`**：

```java
private void sendApprovalReminderNotifications() {
    LocalDateTime threshold = LocalDateTime.now().minusDays(props.getApproval().getOverdueDays());
    List<AnalysisOrder> orders = analysisOrderRepository
        .findByStatusAndStatusChangedAtLessThanEqual(STATUS_PENDING_APPROVAL, threshold);

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
            .map(ReturnOrder::getOrderNumber).orElse(order.getOrderId());
        String subject = String.format("[WFAM] 审批超期提醒 - 退货单 %s - 审批已等待 %d 天",
            orderNumber, daysPending);
        String content = buildApprovalReminderEmail(order, orderNumber, daysPending);

        sendAndLog(null, order.getId(), TYPE_APPROVAL_REMINDER, recipients, List.of(), subject, content);
    }
}
```

**(d) 新增邮件构造** `buildApprovalReminderEmail(AnalysisOrder order, String orderNumber, long days)`：

显示退货单号、批次 analyst、等待审批天数；"请尽快完成审批"。

**(e) 依赖注入**：`NotificationService` 新增 `AnalysisOrderRepository` 与 `ReturnOrderRepository` 两个 final 字段（构造器注入，沿用现有 Lombok `@RequiredArgsConstructor` 或显式构造器——按现有风格）。

### 2.4 文档同步

- **设计文档 `01-设计文档/开发设计文档.md`**：
  - APMS_NOTIFICATION_LOG ER 图与数据字典加 `ANALYSIS_ORDER_ID` 列；`PART_ID` 改"可空"；
  - 2.2.8 通知章节：审批提醒判定改为"查 AnalysisOrder（status=pending_approval 且 statusChangedAt ≤ 阈值）"，按批次去重；
  - 数据字典 STATUS 枚举仍为 `SENT / FAILED`；
  - 新增 v5.3 变更记录。
- **测试文档 `02-工作进度/测试文档.md`**：435/436/437 状态保持 `⏳ 待测试`，备注标"v5.3 已修复，待实测"。
- **手册 `7.4.2-定时通知-测试操作手册.md`**：§4"已知缺陷"改为"v5.3 已修复"；新增 435/436/437 操作章节（构造 pending_approval 批次、调 STATUS_CHANGED_AT / SENT_AT 的 Oracle SQL）。

## 3. 测试方法（修复后）

沿用预警/超期的"改时间戳"套路，配合配置 B 类似的小阈值（可在 local yml 临时设 `approval.overdue-days: 0` 让任何过去时间都满足）。

- **435**：`UPDATE APMS_ANALYSIS_ORDER SET STATUS='pending_approval', STATUS_CHANGED_AT=SYSDATE-1 WHERE ID='<某批次>'; COMMIT;` → 等 cron → 命中 APPROVAL，发提醒给 QMC Leader，日志表新增 APPROVAL_REMINDER/SENT（ANALYSIS_ORDER_ID 有值，PART_ID 为 NULL）。
- **436**：把上次 APPROVAL_REMINDER 的 SENT_AT 改 SYSDATE-4（超 3 天频率）→ 再发。
- **437**：把该批次 STATUS 改 `approved`（或 rejected）→ cron 不再命中 → 不发。

观测判据与现有定时用例一致：看 `APPROVAL scan / check / dispatched / skip` 日志行 + 查 `APMS_NOTIFICATION_LOG`。

## 4. 影响面与回滚

- **影响**：仅审批提醒功能。预警/超期/责任/0km 走各自的 partId 分支，`sendAndLog` 签名加参数为兼容性变更（现有调用点补 `null`）。
- **schema**：APMS_NOTIFICATION_LOG 加列 + PART_ID 放宽可空，向后兼容（旧数据不受影响）。
- **回滚**：代码 revert + 迁移 V53 删除（或新增 V54 drop column）。PART_ID 已有数据非空，回滚 MODIFY NOT NULL 需确认无 NULL 行。

## 5. 不在本次范围

- 不改事件触发型通知（责任判定 / 0km）。
- 不引入批次级"汇总成一封邮件"（保持每批次一封，与现有 per-part 通知风格一致）。
- 不给 APPROVAL_REMINDER 加 analyst 抄送（文档定义为仅 QMC Leader）。
