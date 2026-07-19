-- 关联业务：在统一维修表决切换前，逐项目盘点旧楼栋征询、旧业主大会关联和新统一表决事实。

WITH project_voting AS (
    SELECT project_id,
           string_agg(DISTINCT flow_type, ',' ORDER BY flow_type) AS flow_types,
           string_agg(DISTINCT flow_status, ',' ORDER BY flow_status) AS flow_statuses,
           bool_or(in_progress) AS has_in_progress,
           bool_or(unified) AS has_unified
    FROM (
        SELECT project_id,
               'UNIFIED'::text AS flow_type,
               status::text AS flow_status,
               status IN ('PREPARED', 'VOTING') AS in_progress,
               true AS unified
        FROM t_repair_project_voting
        UNION ALL
        SELECT project_id,
               'LEGACY_BUILDING'::text,
               status::text,
               status NOT IN ('DECISION_FAILED', 'AUTHORIZED'),
               false
        FROM t_repair_building_process
        UNION ALL
        SELECT project_id,
               'LEGACY_ASSEMBLY_LINK'::text,
               status::text,
               status = 'LINKED',
               false
        FROM t_repair_assembly_subject_link
    ) flow
    GROUP BY project_id
)
SELECT project.tenant_id,
       project.project_id,
       project.project_no,
       project.project_name,
       project.status AS project_status,
       COALESCE(voting.flow_types, 'NONE') AS voting_flow_types,
       COALESCE(voting.flow_statuses, 'NONE') AS voting_flow_statuses,
       CASE
           WHEN voting.has_unified THEN 'UNIFIED'
           WHEN voting.has_in_progress THEN 'LEGACY_REQUIRES_MANUAL_DECISION'
           WHEN voting.project_id IS NOT NULL THEN 'LEGACY_READ_ONLY'
           ELSE 'NO_VOTING_RECORD'
       END AS migration_classification,
       project.update_time
FROM t_repair_project project
LEFT JOIN project_voting voting ON voting.project_id = project.project_id
ORDER BY project.tenant_id, project.project_id;

-- 旧维修工单表决不具备新表决包所需的冻结名册、规则快照和逐事项证据，只做单独盘点。
SELECT work_order.tenant_id,
       work_order.work_order_id,
       work_order.order_no,
       work_order.title,
       decision.decision_id,
       decision.result AS legacy_decision_status,
       decision.decision_channel,
       decision.create_time
FROM t_repair_local_decision decision
JOIN t_repair_work_order work_order ON work_order.work_order_id = decision.work_order_id
WHERE decision.project_id IS NULL
ORDER BY work_order.tenant_id, decision.decision_id;

SELECT work_order.tenant_id,
       work_order.work_order_id,
       work_order.order_no,
       work_order.title,
       decision.repair_assembly_decision_id,
       decision.package_id,
       decision.result,
       decision.create_time
FROM t_repair_assembly_decision decision
JOIN t_repair_work_order work_order ON work_order.work_order_id = decision.work_order_id
ORDER BY work_order.tenant_id, decision.repair_assembly_decision_id;
