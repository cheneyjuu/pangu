package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 业主名册查询行 → {@code OwnerProfile} 中转（M4 读侧聚合）。
 *
 * <p>{@code realNameCipher} 保留 SM4 密文原值（不挂 {@code Sm4EncryptTypeHandler}，
 * 由 application 层 {@link com.pangu.domain.security.NameDecryptor} 容错解密，
 * 避免开发期 MOCK_ 明文触发 500）。
 */
@Data
public class OwnerProfileRow {

    private Long uid;
    private Long accountId;
    private String phone;
    private String realNameCipher;
    private Integer realNameVerified;
    private Integer authLevel;
    private Integer accountStatus;
    private Integer propertyCount;
    private BigDecimal totalBuildArea;
    private LocalDateTime createTime;
}
