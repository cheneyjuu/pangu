package com.pangu.domain.gateway;

import com.pangu.domain.common.Page;
import com.pangu.domain.gateway.dto.OwnerQuery;
import com.pangu.domain.model.asset.OwnerProfile;
import com.pangu.domain.model.asset.OwnerPropertyDetail;
import com.pangu.domain.model.asset.OwnerSummary;
import com.pangu.domain.model.asset.PropertyOwnership;

import java.util.List;
import java.util.Optional;

/**
 * 房产业主资产关系领域网关接口
 */
public interface PropertyGateway {

    /**
     * 判断某个自然人用户在特定小区名下是否存在任意欠费房产记录
     */
    boolean hasUnpaidFees(Long uid, Long tenantId);

    /**
     * 获取业主名下的房产绑定列表
     */
    List<PropertyOwnership> getOwnerships(Long uid, Long tenantId);

    /**
     * 按手机号片段（前缀 / 中段 / 尾号）模糊检索本小区业主，用于候选人提名定位。
     *
     * <p>SQL 走 {@code phone LIKE '%xxx%'}（虽不能完全命中 {@code idx_account_phone} 前缀索引，
     * 但租户内业主规模小，整张表片段扫描成本可接受），上限 20 条。
     *
     * @param phoneFragment 手机号任意位置出现的数字片段（明文）
     * @param tenantId      小区租户 ID
     * @return 命中业主摘要列表；{@code realName} 在此链路保持 {@code null}（不解密）
     */
    List<OwnerSummary> searchOwnersByPhone(String phoneFragment, Long tenantId);

    /**
     * 拉取本租户全部可提名业主（用于姓名 / 拼音搜索的 application 层预扫描池）。
     *
     * <p>由 application 层调用 {@code NameDecryptor} 解密 {@code real_name} 后做姓名 / 拼音
     * 全/首字母匹配；密文走 raw 字符串、不挂 SM4 TypeHandler，保留密文交给上层。
     *
     * @param tenantId 小区租户 ID（仅返回在该租户名下有房产的业主）
     * @param limit    预扫描上限（避免单租户业主过多时拖慢搜索；典型值 1000）
     * @return 全部业主摘要（含密文 {@code realName}，由 application 解密替换为明文）
     */
    List<OwnerSummary> listAllNominatableOwners(Long tenantId, int limit);

    /**
     * 业主名册分页查询（M4 读侧）。
     *
     * <p>支持手机号前缀 / 楼栋 / 房号过滤，行级数据范围由 {@code @DataScope} 注入。
     * 排序：{@code uid DESC}（新业主优先），与现有读侧默认一致。
     */
    Page<OwnerProfile> pageOwners(OwnerQuery query);

    /**
     * 按 uid + 租户查询业主名册详情聚合（不含房产明细，房产明细单独走
     * {@link #listPropertiesByUid(Long, Long)}）。
     */
    Optional<OwnerProfile> findOwnerProfile(Long uid, Long tenantId);

    /**
     * 业主在指定租户下的房产明细（详情页用，含楼栋/面积/代表权/账户状态）。
     */
    List<OwnerPropertyDetail> listPropertiesByUid(Long uid, Long tenantId);
}
