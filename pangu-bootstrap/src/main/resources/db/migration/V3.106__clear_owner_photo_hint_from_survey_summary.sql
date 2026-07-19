-- 关联业务：清除将业主上传照片提示误写为现场勘验结论的历史未勘验报修数据。
-- 仅处理尚未完成现场勘验的业主端工单，不覆盖物业人员已经提交的勘验结论。
UPDATE t_repair_work_order
SET survey_summary = NULL
WHERE source = 'C_OWNER_APP'
  AND status IN (
      'SUBMITTED',
      'NEED_MANUAL_LOCATION',
      'PENDING_VERIFY',
      'VERIFIED',
      'ASSIGNED',
      'SURVEYING'
  )
  AND survey_summary ~ '^业主端提交[[:space:]]*[0-9]+[[:space:]]*张现场照片$';
