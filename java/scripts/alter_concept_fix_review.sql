-- =============================================================
-- 概念审批审查修复（2026-08-29，幂等）
-- 1. concept_trace.operator_id 可空：系统动作（超时扫描）无操作人
--    （K8 修复只改了库与 init_db.sql，alter 脚本漏同步——两份脚本矛盾）
-- 2. scanTimeout 查询索引：status IN (2,3,4) AND deadline < now()
-- =============================================================

ALTER TABLE concept_trace ALTER COLUMN operator_id DROP NOT NULL;

CREATE INDEX IF NOT EXISTS ix_concept_status_deadline
    ON concept_session (status, deadline) WHERE status IN (2, 3, 4);
