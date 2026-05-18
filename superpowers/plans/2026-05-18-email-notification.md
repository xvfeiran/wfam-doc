# 邮件通知功能 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现 5 类邮件通知（2 事件触发 + 3 定时任务），含通知日志记录、频率控制和前端通知配置 Tab 移除。

**Architecture:** 在现有 EmailService 基础上新增 NotificationService 统一处理通知逻辑。事件触发通知直接在业务 Service 中调用；定时任务通过 Spring @Scheduled 在凌晨扫描。新增 APMS_NOTIFICATION_LOG 表记录发送历史，用于频率控制。邮件地址通过 application.yml 配置 email-domain 将 loginName 转换为邮箱地址。

**Tech Stack:** Spring Boot 4.0.1 / Spring Data JPA / @Scheduled / Oracle / Flyway / Vue 3 + Ant Design Vue

**设计文档引用:** `doc/01-设计文档/开发设计文档.md` 2.2.8.9 邮件通知

---

## 关键设计决策

### loginName → 邮箱地址映射

系统中 `Part.analyst`、`Part.responsibleEngineer` 存储的是 NT 账号（如 `ZRN7SZH`），不是邮箱地址。系统内没有用户目录 API。

**方案：** 在 application.yml 中配置 `email-suffix`，发送邮件时拼接 `{loginName}@{email-suffix}`（如 `ZRN7SZH@cn.bosch.com`）。如果将来有用户目录 API，只需改这一个方法。

```yaml
custom:
  notification:
    email-suffix: "cn.bosch.com"  # 邮箱后缀，拼接规则: {loginName}@{email-suffix}
```

### QMC_Leader 收件人获取

系统中没有按角色查用户的 API。**方案：** 在 application.yml 中直接配置 QMC_Leader 的邮箱地址列表（逗号分隔），避免引入复杂的用户目录集成。

```yaml
custom:
  notification:
    qmc-leader-emails: "leader1@cn.bosch.com,leader2@cn.bosch.com"
```

### 0km 判断

`APMS_PART.COMPLAINT_TYPE` 列在数据库存在但 Java `Part` 实体未映射。0km 类型为 `BA10, BA20, BA21, BA30, BA31`。

**方案：** 0km 通知改为在售后件创建时检查 `PartDTO` 中是否关联的退货单 complaintType 为 0km 类型。通过 ReturnOrder 的 complaintType 判断（已有 Java 字段映射）。

### Dashboard 预警阈值

当前 `DashboardMetricsService` 硬编码 warning=5天, overdue=10天。需要改为从 yml 读取，与通知共享同一配置。

---

## File Structure

### Backend 新增文件

| 文件 | 职责 |
|------|------|
| `entity/NotificationLog.java` | 通知日志 JPA 实体 |
| `repository/NotificationLogRepository.java` | 通知日志 Repository |
| `service/NotificationService.java` | 通知核心服务（发送、频率控制、事件触发、定时任务） |
| `config/NotificationProperties.java` | yml 配置映射类 |
| `config/SchedulingConfig.java` | 启用 @Scheduled |
| `resources/db/migration/V42__create_notification_log.sql` | 建表 |

### Backend 修改文件

| 文件 | 变更 |
|------|------|
| `service/AnalysisReportService.java` | 保存 RESPONSIBILITY 后触发通知 |
| `service/PartService.java` | 售后件创建时检查 0km 触发通知 |
| `service/DashboardMetricsService.java` | 预警/超期天数改为从 NotificationProperties 读取 |
| `resources/application-local.yml` | 添加 notification 配置 |

### Frontend 修改文件

| 文件 | 变更 |
|------|------|
| `views/settings/Settings.vue` | 移除 notifications Tab |
| `views/settings/components/NotificationConfig.vue` | 删除此文件 |
| `i18n/zh-CN.ts` | 移除通知配置相关翻译 key |
| `i18n/en-US.ts` | 移除通知配置相关翻译 key |

---

## Task 1: NotificationProperties 配置类

**Files:**
- Create: `backend/src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/config/NotificationProperties.java`
- Modify: `backend/src/main/resources/application-local.yml`

- [ ] **Step 1: 创建 NotificationProperties.java**

```java
package com.bosch.rbcc.aftermarketpartsmanagementsystem.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "custom.notification")
public class NotificationProperties {

    private Analysis analysis = new Analysis();
    private Approval approval = new Approval();
    private Frequency frequency = new Frequency();
    private String cron = "0 0 0 * * ?";
    private String emailSuffix = "cn.bosch.com";
    private String qmcLeaderEmails = "";

    @Data
    public static class Analysis {
        private int warningDays = 13;
        private int overdueDays = 21;
    }

    @Data
    public static class Approval {
        private int overdueDays = 3;
    }

    @Data
    public static class Frequency {
        private int warning = 1;
        private int overdue = 3;
        private int approvalReminder = 3;
    }

    public String toEmailAddress(String loginName) {
        if (loginName == null || loginName.isBlank()) return null;
        return loginName.trim() + "@" + emailSuffix;
    }
}
```

- [ ] **Step 2: 更新 application-local.yml，在 `custom:` 下新增 notification 配置**

在 `custom.smb` 块之后添加：

```yaml
  notification:
    email-suffix: "cn.bosch.com"
    qmc-leader-emails: ""                    # QMC Leader 邮箱列表，逗号分隔
    cron: "0 0 0 * * ?"                      # 定时任务执行时间（每日凌晨0点）
    analysis:
      warning-days: 13                       # 精分析预警天数
      overdue-days: 21                       # 精分析超期天数
    approval:
      overdue-days: 3                        # 审批超期天数
    frequency:
      warning: 1                             # 预警邮件频率（天）
      overdue: 3                             # 超期邮件频率（天）
      approval-reminder: 3                   # 审批提醒频率（天）
```

- [ ] **Step 3: 启用 @Scheduled — 创建 SchedulingConfig.java**

```java
package com.bosch.rbcc.aftermarketpartsmanagementsystem.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SchedulingConfig {
}
```

- [ ] **Step 4: Commit**

```bash
cd backend && git add src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/config/NotificationProperties.java src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/config/SchedulingConfig.java src/main/resources/application-local.yml && git commit -m "feat: add notification config properties and scheduling support"
```

---

## Task 2: NotificationLog 实体与 Repository

**Files:**
- Create: `backend/src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/entity/NotificationLog.java`
- Create: `backend/src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/repository/NotificationLogRepository.java`
- Create: `backend/src/main/resources/db/migration/V42__create_notification_log.sql`

- [ ] **Step 1: 创建 Flyway 迁移脚本 V42__create_notification_log.sql**

```sql
CREATE TABLE APMS_NOTIFICATION_LOG (
    ID                VARCHAR2(36) NOT NULL,
    PART_ID           VARCHAR2(36) NOT NULL,
    NOTIFICATION_TYPE VARCHAR2(30) NOT NULL,
    RECIPIENTS        VARCHAR2(500) NOT NULL,
    CC_RECIPIENTS     VARCHAR2(500),
    STATUS            VARCHAR2(10) NOT NULL,
    SENT_AT           TIMESTAMP NOT NULL,
    ERROR_MESSAGE     VARCHAR2(500),
    CONSTRAINT PK_NOTIFICATION_LOG PRIMARY KEY (ID)
);

CREATE INDEX IDX_NOTIFICATION_PART_TYPE ON APMS_NOTIFICATION_LOG (PART_ID, NOTIFICATION_TYPE);

COMMENT ON TABLE APMS_NOTIFICATION_LOG IS '邮件通知发送记录';
COMMENT ON COLUMN APMS_NOTIFICATION_LOG.NOTIFICATION_TYPE IS '通知类型: WARNING/OVERDUE/APPROVAL_REMINDER/RESPONSIBILITY/ZERO_KM';
COMMENT ON COLUMN APMS_NOTIFICATION_LOG.STATUS IS '发送状态: SUCCESS/FAILED';
```

- [ ] **Step 2: 创建 NotificationLog.java 实体**

```java
package com.bosch.rbcc.aftermarketpartsmanagementsystem.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;

import java.time.LocalDateTime;

@Entity
@Table(name = "APMS_NOTIFICATION_LOG")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationLog {

    @Id
    @Column(name = "ID", length = 36)
    private String id;

    @Column(name = "PART_ID", length = 36, nullable = false)
    private String partId;

    @Column(name = "NOTIFICATION_TYPE", length = 30, nullable = false)
    private String notificationType;

    @Column(name = "RECIPIENTS", length = 500, nullable = false)
    private String recipients;

    @Column(name = "CC_RECIPIENTS", length = 500)
    private String ccRecipients;

    @Column(name = "STATUS", length = 10, nullable = false)
    private String status;

    @Column(name = "SENT_AT", nullable = false)
    private LocalDateTime sentAt;

    @Column(name = "ERROR_MESSAGE", length = 500)
    private String errorMessage;
}
```

- [ ] **Step 3: 创建 NotificationLogRepository.java**

```java
package com.bosch.rbcc.aftermarketpartsmanagementsystem.repository;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, String> {

    Optional<NotificationLog> findTopByPartIdAndNotificationTypeOrderBySentAtDesc(String partId, String notificationType);

    boolean existsByPartIdAndNotificationTypeAndSentAtAfter(String partId, String notificationType, LocalDateTime after);
}
```

- [ ] **Step 4: Commit**

```bash
cd backend && git add src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/entity/NotificationLog.java src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/repository/NotificationLogRepository.java src/main/resources/db/migration/V42__create_notification_log.sql && git commit -m "feat: add NotificationLog entity, repository and migration"
```

---

## Task 3: NotificationService — 核心服务

**Files:**
- Create: `backend/src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/service/NotificationService.java`

- [ ] **Step 1: 创建 NotificationService.java**

```java
package com.bosch.rbcc.aftermarketpartsmanagementsystem.service;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.config.NotificationProperties;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.NotificationLog;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.Part;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.NotificationLogRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.PartRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final String TYPE_WARNING = "WARNING";
    private static final String TYPE_OVERDUE = "OVERDUE";
    private static final String TYPE_APPROVAL_REMINDER = "APPROVAL_REMINDER";
    private static final String TYPE_RESPONSIBILITY = "RESPONSIBILITY";
    private static final String TYPE_ZERO_KM = "ZERO_KM";

    private static final List<String> ZERO_KM_TYPES = List.of("BA10", "BA20", "BA21", "BA30", "BA31");

    // Part status constants - must match DashboardMetricsService
    private static final String STATUS_IN_DETAILED_ANALYSIS = "in_detailed_analysis";
    private static final String STATUS_PENDING_APPROVAL = "pending_approval";

    private final EmailService emailService;
    private final NotificationProperties props;
    private final NotificationLogRepository logRepo;
    private final PartRepository partRepository;

    // ========== Event-triggered notifications ==========

    /**
     * 责任判定 B/O 通知：精分析报告保存 RESPONSIBILITY 为 B 或 O 时触发
     */
    public void sendResponsibilityNotification(String partId, String responsibility) {
        if (responsibility == null || (!responsibility.equalsIgnoreCase("B") && !responsibility.equalsIgnoreCase("O"))) {
            return;
        }

        Part part = partRepository.findById(partId).orElse(null);
        if (part == null) {
            log.warn("Part not found for responsibility notification: {}", partId);
            return;
        }

        List<String> recipients = new ArrayList<>();
        List<String> ccList = new ArrayList<>();

        // 收件人: ANALYST + RESPONSIBLE_ENGINEER
        addEmailIfExists(recipients, part.getAnalyst());
        addEmailIfExists(recipients, part.getResponsibleEngineer());

        // 抄送: QMC Leader
        addQmcLeaders(ccList);

        if (recipients.isEmpty()) {
            log.warn("No recipients for responsibility notification, partId={}", partId);
            return;
        }

        String subject = String.format("[WFAM] 责任判定通知 - 售后件 %s - 责任判定: %s",
            part.getPartNumber(), responsibility);
        String content = buildResponsibilityEmail(part, responsibility);

        sendAndLog(partId, TYPE_RESPONSIBILITY, recipients, ccList, subject, content);
    }

    /**
     * 0公里退货通知：售后件创建时 COMPLAINT_TYPE 为 0km 类型触发
     */
    public void sendZeroKmNotification(String partId, String orderComplaintType) {
        if (orderComplaintType == null || !ZERO_KM_TYPES.contains(orderComplaintType.toUpperCase())) {
            return;
        }

        Part part = partRepository.findById(partId).orElse(null);
        if (part == null) {
            log.warn("Part not found for 0km notification: {}", partId);
            return;
        }

        List<String> recipients = new ArrayList<>();
        addEmailIfExists(recipients, part.getResponsibleEngineer());

        if (recipients.isEmpty()) {
            log.warn("No recipients for 0km notification, partId={}", partId);
            return;
        }

        String subject = String.format("[WFAM] 0公里退货通知 - 售后件 %s", part.getPartNumber());
        String content = buildZeroKmEmail(part, orderComplaintType);

        sendAndLog(partId, TYPE_ZERO_KM, recipients, List.of(), subject, content);
    }

    // ========== Scheduled notifications ==========

    /**
     * 定时任务：每日凌晨扫描，发送预警/超时/审批超期通知
     */
    @Scheduled(cron = "${custom.notification.cron:0 0 0 * * ?}")
    public void scheduledNotificationCheck() {
        log.info("Scheduled notification check started");
        try {
            sendWarningNotifications();
            sendOverdueNotifications();
            sendApprovalReminderNotifications();
        } catch (Exception e) {
            log.error("Scheduled notification check failed: {}", e.getMessage(), e);
        }
        log.info("Scheduled notification check completed");
    }

    private void sendWarningNotifications() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(props.getAnalysis().getWarningDays());
        List<Part> parts = partRepository.findByStatusAndStatusChangedAtLessThanEqual(
            STATUS_IN_DETAILED_ANALYSIS, threshold);

        for (Part part : parts) {
            if (shouldSkip(part.getId(), TYPE_WARNING, props.getFrequency().getWarning())) {
                continue;
            }

            List<String> recipients = new ArrayList<>();
            addEmailIfExists(recipients, part.getAnalyst());

            if (recipients.isEmpty()) continue;

            long daysInAnalysis = java.time.Duration.between(part.getStatusChangedAt(), LocalDateTime.now()).toDays();
            String subject = String.format("[WFAM] 精分析预警 - 售后件 %s - 已进入精分析 %d 天",
                part.getPartNumber(), daysInAnalysis);
            String content = buildWarningEmail(part, daysInAnalysis);

            sendAndLog(part.getId(), TYPE_WARNING, recipients, List.of(), subject, content);
        }
    }

    private void sendOverdueNotifications() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(props.getAnalysis().getOverdueDays());
        List<Part> parts = partRepository.findByStatusAndStatusChangedAtLessThanEqual(
            STATUS_IN_DETAILED_ANALYSIS, threshold);

        for (Part part : parts) {
            if (shouldSkip(part.getId(), TYPE_OVERDUE, props.getFrequency().getOverdue())) {
                continue;
            }

            List<String> recipients = new ArrayList<>();
            addEmailIfExists(recipients, part.getAnalyst());

            List<String> ccList = new ArrayList<>();
            addQmcLeaders(ccList);

            if (recipients.isEmpty()) continue;

            long daysInAnalysis = java.time.Duration.between(part.getStatusChangedAt(), LocalDateTime.now()).toDays();
            String subject = String.format("[WFAM] 精分析超期 - 售后件 %s - 已超期 %d 天",
                part.getPartNumber(), daysInAnalysis - props.getAnalysis().getOverdueDays());
            String content = buildOverdueEmail(part, daysInAnalysis);

            sendAndLog(part.getId(), TYPE_OVERDUE, recipients, ccList, subject, content);
        }
    }

    private void sendApprovalReminderNotifications() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(props.getApproval().getOverdueDays());
        List<Part> parts = partRepository.findByStatusAndStatusChangedAtLessThanEqual(
            STATUS_PENDING_APPROVAL, threshold);

        for (Part part : parts) {
            if (shouldSkip(part.getId(), TYPE_APPROVAL_REMINDER, props.getFrequency().getApprovalReminder())) {
                continue;
            }

            List<String> recipients = new ArrayList<>();
            addQmcLeaders(recipients);

            if (recipients.isEmpty()) continue;

            long daysPending = java.time.Duration.between(part.getStatusChangedAt(), LocalDateTime.now()).toDays();
            String subject = String.format("[WFAM] 审批超期提醒 - 售后件 %s - 审批已等待 %d 天",
                part.getPartNumber(), daysPending);
            String content = buildApprovalReminderEmail(part, daysPending);

            sendAndLog(part.getId(), TYPE_APPROVAL_REMINDER, recipients, List.of(), subject, content);
        }
    }

    // ========== Frequency control ==========

    private boolean shouldSkip(String partId, String type, int frequencyDays) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(frequencyDays);
        return logRepo.existsByPartIdAndNotificationTypeAndSentAtAfter(partId, type, cutoff);
    }

    // ========== Send + Log ==========

    private void sendAndLog(String partId, String type, List<String> recipients, List<String> ccList,
                            String subject, String content) {
        String recipientsStr = String.join(";", recipients);
        String ccStr = ccList.isEmpty() ? null : String.join(";", ccList);

        try {
            for (String to : recipients) {
                emailService.sendHtmlEmail(to, subject, content);
            }
            // Also send to CC recipients
            if (!ccList.isEmpty()) {
                for (String cc : ccList) {
                    emailService.sendHtmlEmail(cc, subject, content);
                }
            }
            logRepo.save(NotificationLog.builder()
                .id(UUID.randomUUID().toString())
                .partId(partId)
                .notificationType(type)
                .recipients(recipientsStr)
                .ccRecipients(ccStr)
                .status("SUCCESS")
                .sentAt(LocalDateTime.now())
                .build());
            log.info("Notification sent: type={}, partId={}, recipients={}", type, partId, recipientsStr);
        } catch (Exception e) {
            logRepo.save(NotificationLog.builder()
                .id(UUID.randomUUID().toString())
                .partId(partId)
                .notificationType(type)
                .recipients(recipientsStr)
                .ccRecipients(ccStr)
                .status("FAILED")
                .sentAt(LocalDateTime.now())
                .errorMessage(e.getMessage())
                .build());
            log.error("Notification failed: type={}, partId={}, error={}", type, partId, e.getMessage());
        }
    }

    // ========== Email content builders ==========

    private String buildResponsibilityEmail(Part part, String responsibility) {
        return String.format("""
            <h3>责任判定通知</h3>
            <p>售后件 <strong>%s</strong> 的精分析责任判定结果为：<strong>%s</strong></p>
            <table border="1" cellpadding="5" cellspacing="0">
            <tr><td>售后件编号</td><td>%s</td></tr>
            <tr><td>零件号</td><td>%s</td></tr>
            <tr><td>业务单元</td><td>%s</td></tr>
            <tr><td>产品平台</td><td>%s</td></tr>
            <tr><td>责任判定</td><td>%s</td></tr>
            </table>
            <p>请及时登录 WFAM 系统查看详情。</p>
            """,
            part.getPartNumber(), responsibility,
            part.getPartNumber(), part.getPartCode(),
            part.getBusinessUnit(), part.getProductPlatform(),
            "B".equalsIgnoreCase(responsibility) ? "Bosch" : "OEM");
    }

    private String buildZeroKmEmail(Part part, String complaintType) {
        return String.format("""
            <h3>0公里退货通知</h3>
            <p>收到一笔 0公里退货，请及时关注：</p>
            <table border="1" cellpadding="5" cellspacing="0">
            <tr><td>售后件编号</td><td>%s</td></tr>
            <tr><td>零件号</td><td>%s</td></tr>
            <tr><td>业务单元</td><td>%s</td></tr>
            <tr><td>产品平台</td><td>%s</td></tr>
            <tr><td>退货类型</td><td>%s</td></tr>
            </table>
            <p>请及时登录 WFAM 系统处理。</p>
            """,
            part.getPartNumber(), part.getPartCode(),
            part.getBusinessUnit(), part.getProductPlatform(),
            complaintType);
    }

    private String buildWarningEmail(Part part, long days) {
        return String.format("""
            <h3>精分析预警通知</h3>
            <p>售后件 <strong>%s</strong> 精分析已进行 <strong>%d</strong> 天，即将超期。</p>
            <table border="1" cellpadding="5" cellspacing="0">
            <tr><td>售后件编号</td><td>%s</td></tr>
            <tr><td>零件号</td><td>%s</td></tr>
            <tr><td>已进行天数</td><td>%d</td></tr>
            <tr><td>超期阈值</td><td>%d 天</td></tr>
            </table>
            <p>请尽快完成精分析报告。</p>
            """,
            part.getPartNumber(), days,
            part.getPartNumber(), part.getPartCode(),
            days, props.getAnalysis().getOverdueDays());
    }

    private String buildOverdueEmail(Part part, long days) {
        return String.format("""
            <h3>精分析超期通知</h3>
            <p>售后件 <strong>%s</strong> 精分析已超期！已进行 <strong>%d</strong> 天（超期阈值 %d 天）。</p>
            <table border="1" cellpadding="5" cellspacing="0">
            <tr><td>售后件编号</td><td>%s</td></tr>
            <tr><td>零件号</td><td>%s</td></tr>
            <tr><td>已进行天数</td><td>%d</td></tr>
            <tr><td>超期天数</td><td>%d</td></tr>
            </table>
            <p style="color:red;"><strong>请立即处理！</strong></p>
            """,
            part.getPartNumber(), days, props.getAnalysis().getOverdueDays(),
            part.getPartNumber(), part.getPartCode(),
            days, days - props.getAnalysis().getOverdueDays());
    }

    private String buildApprovalReminderEmail(Part part, long days) {
        return String.format("""
            <h3>审批超期提醒</h3>
            <p>售后件 <strong>%s</strong> 的精分析报告审批已等待 <strong>%d</strong> 天。</p>
            <table border="1" cellpadding="5" cellspacing="0">
            <tr><td>售后件编号</td><td>%s</td></tr>
            <tr><td>零件号</td><td>%s</td></tr>
            <tr><td>等待审批天数</td><td>%d</td></tr>
            </table>
            <p>请尽快完成审批。</p>
            """,
            part.getPartNumber(), days,
            part.getPartNumber(), part.getPartCode(), days);
    }

    // ========== Helpers ==========

    private void addEmailIfExists(List<String> list, String loginName) {
        String email = props.toEmailAddress(loginName);
        if (email != null) {
            list.add(email);
        }
    }

    private void addQmcLeaders(List<String> list) {
        String emails = props.getQmcLeaderEmails();
        if (emails != null && !emails.isBlank()) {
            Arrays.stream(emails.split(","))
                .map(String::trim)
                .filter(e -> !e.isEmpty())
                .forEach(list::add);
        }
    }
}
```

- [ ] **Step 2: 在 PartRepository 中添加按状态查询方法**

在 `PartRepository.java` 中添加：

```java
List<Part> findByStatusAndStatusChangedAtLessThanEqual(String status, LocalDateTime statusChangedAt);
```

- [ ] **Step 3: Commit**

```bash
cd backend && git add src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/service/NotificationService.java src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/repository/PartRepository.java && git commit -m "feat: add NotificationService with event-triggered and scheduled notifications"
```

---

## Task 4: 业务 Service 集成事件触发通知

**Files:**
- Modify: `backend/src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/service/AnalysisReportService.java:84-89`
- Modify: `backend/src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/service/PartService.java:174`

- [ ] **Step 1: 在 AnalysisReportService 中注入 NotificationService 并触发责任判定通知**

在 `AnalysisReportService` 类中添加字段注入（使用 `@RequiredArgsConstructor` 已有，只需添加 final 字段）：

```java
private final NotificationService notificationService;
```

在 `createOrUpdate()` 方法中，RESPONSIBILITY JDBC update 之后（约 Line 89），添加：

```java
// 触发责任判定通知
notificationService.sendResponsibilityNotification(report.getPartId(), dto.getResponsibility());
```

注意：`report.getPartId()` 需要确认是否可直接获取。查看 AnalysisReport 实体，`partId` 字段存在。

- [ ] **Step 2: 在 PartService 中注入 NotificationService 并触发 0km 通知**

在 `PartService` 类中添加字段：

```java
private final NotificationService notificationService;
```

在 `create()` 方法中（约 Line 174），在 `partRepo.save(part)` 之后，添加：

```java
// 检查是否为 0km 退货，触发通知
if (dto.getOrderId() != null) {
    returnOrderRepo.findById(dto.getOrderId()).ifPresent(order -> {
        notificationService.sendZeroKmNotification(part.getId(), order.getComplaintType());
    });
}
```

需要确保 `PartService` 中已注入 `ReturnOrderRepository`。如果未注入，需添加。

- [ ] **Step 3: 编译验证**

```bash
cd backend && mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
cd backend && git add src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/service/AnalysisReportService.java src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/service/PartService.java && git commit -m "feat: integrate notification triggers into AnalysisReportService and PartService"
```

---

## Task 5: Dashboard 预警阈值改为配置化

**Files:**
- Modify: `backend/src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/service/DashboardMetricsService.java:96-99,118-122`

- [ ] **Step 1: 在 DashboardMetricsService 中注入 NotificationProperties**

添加字段：

```java
private final NotificationProperties notificationProperties;
```

- [ ] **Step 2: 替换硬编码阈值为配置值**

在 `getTasks()` 方法中（Line 96-99），替换：

```java
// Before:
LocalDateTime warningThreshold = now.minusDays(5);
LocalDateTime overdueThreshold = now.minusDays(10);

// After:
LocalDateTime warningThreshold = now.minusDays(notificationProperties.getAnalysis().getWarningDays());
LocalDateTime overdueThreshold = now.minusDays(notificationProperties.getAnalysis().getOverdueDays());
```

在 `getTasks(String analyst, String roleNames)` 方法中（Line 119-122），做同样替换。

- [ ] **Step 3: 编译验证**

```bash
cd backend && mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
cd backend && git add src/main/java/com/bosch/rbcc/aftermarketpartsmanagementsystem/service/DashboardMetricsService.java && git commit -m "refactor: dashboard warning/overdue thresholds from hardcoded to yml config"
```

---

## Task 6: 前端移除通知配置 Tab

**Files:**
- Modify: `frontend/src/views/settings/Settings.vue`
- Delete: `frontend/src/views/settings/components/NotificationConfig.vue`
- Modify: `frontend/src/i18n/zh-CN.ts`
- Modify: `frontend/src/i18n/en-US.ts`

- [ ] **Step 1: 在 Settings.vue 中移除 notifications Tab**

删除 `<a-tab-pane key="notifications" ...>` 整个 tab-pane 块（约 Line 17-23）。

删除 `import NotificationConfig` 语句。

删除 `notificationConfig` reactive 对象（约 Line 232-237）。

删除 `handleSaveNotificationConfig` 方法。

删除 `userOptions` 相关代码（如果仅用于通知配置）。

- [ ] **Step 2: 删除 NotificationConfig.vue**

```bash
rm frontend/src/views/settings/components/NotificationConfig.vue
```

- [ ] **Step 3: 清理 i18n 翻译 key**

在 `zh-CN.ts` 中删除以下 key（如果不再被其他地方使用）：

```typescript
settings.notificationConfig: '邮件触发周期配置',
settings.warningNotification: '即将超期通知',
settings.overdueNotification: '超期通知',
settings.cronExpression: '任务运行Cron表达式',
settings.cronTip: '例如：0 9 * * * 表示每天9点执行',
settings.warningThreshold: '即将超期阈值（天）',
settings.thresholdTip: '距离超期多少天时发送预警通知',
settings.recipients: '通知接收人',
```

保留 `settings.notifications: '邮件通知'`（可能其他地方引用，但 Tab 已移除，也可以删除）。

在 `en-US.ts` 中删除对应英文 key。

- [ ] **Step 4: 验证前端编译**

```bash
cd frontend && npm run build
```

Expected: 构建成功，无编译错误

- [ ] **Step 5: Commit**

```bash
cd frontend && git add -A && git commit -m "feat: remove notification config tab from settings page"
```

---

## Task 7: 全量编译与验证

**Files:** 无新增

- [ ] **Step 1: 后端全量编译**

```bash
cd backend && mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 2: 前端全量编译**

```bash
cd frontend && npm run build
```

Expected: 构建成功

- [ ] **Step 3: 提交文档更新**

设计文档和测试文档已在之前的步骤中更新，确认已提交：

```bash
cd doc && git add -A && git commit -m "docs: add email notification design (v4.13) and test cases (v3.14)" 2>/dev/null || echo "Doc changes already committed or no doc repo"
```

---

## Self-Review

### 1. Spec coverage

| 需求 | 对应 Task |
|------|----------|
| 责任判定 B/O 通知 | Task 3 (sendResponsibilityNotification) + Task 4 (AnalysisReportService hook) |
| 0公里退货通知 | Task 3 (sendZeroKmNotification) + Task 4 (PartService hook) |
| 时限预警通知 | Task 3 (sendWarningNotifications + @Scheduled) |
| 时限超时通知 | Task 3 (sendOverdueNotifications + @Scheduled) |
| 审批超期提醒 | Task 3 (sendApprovalReminderNotifications + @Scheduled) |
| APMS_NOTIFICATION_LOG 表 | Task 2 |
| application.yml 配置 | Task 1 |
| 前端移除通知配置 Tab | Task 6 |
| Dashboard 阈值配置化 | Task 5 |
| 频率控制 | Task 3 (shouldSkip + existsByPartIdAndNotificationTypeAndSentAtAfter) |

### 2. Placeholder scan

无 TBD / TODO / placeholder。所有步骤包含完整代码。

### 3. Type consistency

- `NotificationLog.notificationType` 使用字符串常量（`TYPE_WARNING` 等），与 Repository 查询方法参数类型一致
- `PartRepository.findByStatusAndStatusChangedAtLessThanEqual` 参数类型 `(String, LocalDateTime)` 与 DashboardMetricsService 中 `countByStatusAndStatusChangedAtLessThanEqual` 保持一致
- `NotificationProperties` 字段名与 yml 配置键名一致（camelCase ↔ kebab-case 自动映射）

### 4. 遗留问题

- `PartService.create()` 中的 `returnOrderRepo` 是否已注入需在实现时确认，如未注入需添加
- `AnalysisReport` 实体中 `partId` 字段需确认可访问（JPA 映射的 `part_id` 列）
