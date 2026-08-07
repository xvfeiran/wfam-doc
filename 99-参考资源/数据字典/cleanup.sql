-- ============================================================
-- 数据字典清理脚本
-- 清空 APMS_CUSTOMER 和 APMS_PART_CODE，准备重新导入
-- 使用方法：在 DBeaver 中连接到 Oracle 后执行此脚本
-- 生成时间：2026-08-06T07:55:14.409Z
-- ============================================================

-- 1. 确认当前数据量
SELECT COUNT(*) AS customer_count_before FROM WFAM_PROD.APMS_CUSTOMER;
SELECT COUNT(*) AS part_code_count_before FROM WFAM_PROD.APMS_PART_CODE;

-- 2. 清理数据
DELETE FROM WFAM_PROD.APMS_PART_CODE;
DELETE FROM WFAM_PROD.APMS_CUSTOMER;

-- 3. 确认清理结果（应为 0）
SELECT COUNT(*) AS customer_count_after FROM WFAM_PROD.APMS_CUSTOMER;
SELECT COUNT(*) AS part_code_count_after FROM WFAM_PROD.APMS_PART_CODE;

COMMIT;
