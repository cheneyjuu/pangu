package com.pangu.domain.gateway;

/**
 * 业委会交接密钥回收端口。
 *
 * <p>当前梯度 C 只需要 mock 钩子：街道办备案通过后触发“老主任密钥回收”副作用。
 * 后续接真实密钥/证书系统时替换 infrastructure 实现即可。
 */
public interface CommitteeKeyRevocationGateway {

    void revokeOutgoingDirectorKeys(Long tenantId, Long confirmedByUserId);
}
