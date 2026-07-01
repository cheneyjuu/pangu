package com.pangu.domain.model.voting;

import java.math.BigDecimal;

/**
 * 投票写入侧监控快照。
 *
 * @param subjectId              议题 ID
 * @param totalCount             Redis 记录的成功写票总数
 * @param unsignedCount          无签名票数，作为纸票/线下票候选基线
 * @param unsignedRatio          无签名票占比，4 位小数
 * @param unsignedRatioThreshold 无签名占比告警阈值
 * @param unsignedRatioAlert     是否触发无签名票占比告警
 * @param rapidIntervalCount     相邻写票间隔小于 30 秒的次数
 * @param rapidIntervalThreshold 快速连续投票次数告警阈值
 * @param rapidIntervalAlert     是否触发快速连续投票告警
 */
public record VoteCastMonitorSnapshot(
        Long subjectId,
        long totalCount,
        long unsignedCount,
        BigDecimal unsignedRatio,
        BigDecimal unsignedRatioThreshold,
        boolean unsignedRatioAlert,
        long rapidIntervalCount,
        long rapidIntervalThreshold,
        boolean rapidIntervalAlert
) {
}
