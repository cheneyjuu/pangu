-- 关联业务：业主大会公共事项不适用委员选举的党员比例字段，删除误入会前事项草案表的冗余列。

ALTER TABLE t_owners_assembly_subject_draft
    DROP COLUMN IF EXISTS party_ratio_floor;
