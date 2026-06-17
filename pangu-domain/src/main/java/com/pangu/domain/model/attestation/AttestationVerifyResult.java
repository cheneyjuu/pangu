package com.pangu.domain.model.attestation;

import java.time.Instant;

/**
 * 司法链存证核验结果。
 *
 * <p>本期不实现核验链路，仅预留接口；真实接入司法链后由 verify 路径填充。
 *
 * @param verified         链上记录与本地 payload 是否一致
 * @param chainPayloadHash 链上记录的 payload hash
 * @param verifiedAt       核验时间戳
 * @param remark           额外说明（不一致时记录差异点）
 */
public record AttestationVerifyResult(
        boolean verified,
        String chainPayloadHash,
        Instant verifiedAt,
        String remark
) {
}
