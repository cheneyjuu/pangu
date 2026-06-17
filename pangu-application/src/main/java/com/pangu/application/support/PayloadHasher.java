package com.pangu.application.support;

import com.pangu.domain.model.waiver.PartyRatioWaiver;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 业务实体 → 上链 payload 的 SHA-256 摘要工具。
 *
 * <p>当前主要用途：waiver 终审通过瞬间，对（waiverId / subjectId / tenantId / requestedRatio /
 * partyPoolSize / totalEligibleSize / committeeApprover / streetApprover / approval timestamps）
 * 进行规范化拼接后取 SHA-256，作为 {@code local_payload_hash} 永久锁定，让事后存证 / 审计可比对。
 *
 * <p>规范化原则：
 * <ul>
 *   <li>固定字段顺序，竖线（{@code |}）分隔；</li>
 *   <li>BigDecimal 用 {@code toPlainString()} 避免科学计数法；</li>
 *   <li>{@code Instant} 统一用 ISO-8601 序列化（毫秒精度）；</li>
 *   <li>null 字段写空字符串（不省略，保持位置）。</li>
 * </ul>
 */
@Component
public class PayloadHasher {

    private static final String ALGO = "SHA-256";

    /**
     * 为 APPROVED 状态的 waiver 计算本地 payload hash。
     *
     * @return 64 位小写 hex
     * @throws IllegalStateException 找不到 SHA-256 算法（JDK 缺陷，理论不发生）
     */
    public String hashWaiverApproval(PartyRatioWaiver w) {
        StringBuilder sb = new StringBuilder(256);
        sb.append(safe(w.getWaiverId())).append('|')
          .append(safe(w.getSubjectId())).append('|')
          .append(safe(w.getTenantId())).append('|')
          .append(safe(w.getInitiatorUserId())).append('|')
          .append(w.getRequestedRatio() == null ? "" : w.getRequestedRatio().toPlainString()).append('|')
          .append(w.getPartyPoolSize()).append('|')
          .append(w.getTotalEligibleSize()).append('|')
          .append(safe(w.getCommitteeApprover())).append('|')
          .append(safe(w.getCommitteeApprovalAt())).append('|')
          .append(safe(w.getStreetApprover())).append('|')
          .append(safe(w.getStreetApprovalAt()));
        return sha256Hex(sb.toString());
    }

    private static String safe(Object o) {
        return o == null ? "" : o.toString();
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance(ALGO);
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }
}
