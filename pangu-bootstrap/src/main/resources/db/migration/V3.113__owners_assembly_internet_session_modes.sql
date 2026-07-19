-- V3.113: 业主大会正式办理支持互联网表决，以及规则明确允许时的线上线下并行表决。

ALTER TABLE t_owners_assembly_session
    DROP CONSTRAINT IF EXISTS chk_owners_assembly_session_mode;

ALTER TABLE t_owners_assembly_session
    ADD CONSTRAINT chk_owners_assembly_session_mode CHECK (
        preparation_mode IN (
            'FULL', 'QUICK',
            'WRITTEN_DECISION', 'OFFLINE_MEETING',
            'INTERNET_DECISION', 'ONLINE_AND_OFFLINE'
        )
    );
