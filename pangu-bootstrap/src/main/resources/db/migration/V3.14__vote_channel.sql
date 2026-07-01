-- Explicit vote source channel. Existing rows are C-end online votes by default.
ALTER TABLE t_vote_item
    ADD COLUMN IF NOT EXISTS vote_channel SMALLINT NOT NULL DEFAULT 1;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_vote_item_channel'
    ) THEN
        ALTER TABLE t_vote_item
            ADD CONSTRAINT chk_vote_item_channel
                CHECK (vote_channel IN (1, 2, 3));
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_vote_item_subject_channel
    ON t_vote_item(subject_id, vote_channel);

COMMENT ON COLUMN t_vote_item.vote_channel IS
    '投票写入通道：1-ONLINE(C端线上), 2-PAPER(纸票), 3-OFFLINE_PROXY(线下代录)';
