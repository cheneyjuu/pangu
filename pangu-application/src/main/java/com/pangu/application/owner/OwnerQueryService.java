package com.pangu.application.owner;

import com.pangu.domain.common.Page;
import com.pangu.domain.gateway.PropertyGateway;
import com.pangu.domain.gateway.dto.OwnerQuery;
import com.pangu.domain.model.asset.OwnerProfile;
import com.pangu.domain.model.asset.OwnerPropertyDetail;
import com.pangu.domain.security.NameDecryptor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 业主名册查询应用服务（M4 读侧）。
 *
 * <p>纯只读：聚合 {@code t_account / c_user / c_owner_property} 三表，
 * 在领域层之上做姓名容错解密（{@link NameDecryptor#safeDecrypt}），最终返回 view DTO。
 *
 * <p>租户与行级数据范围由调用端 + mapper {@code @DataScope} 双保（service 不重复校验）。
 */
@Service
public class OwnerQueryService {

    private final PropertyGateway propertyGateway;
    private final NameDecryptor nameDecryptor;

    public OwnerQueryService(PropertyGateway propertyGateway, NameDecryptor nameDecryptor) {
        this.propertyGateway = propertyGateway;
        this.nameDecryptor = nameDecryptor;
    }

    /** 业主名册分页查询。 */
    public Page<OwnerProfileView> pageOwners(OwnerQuery query) {
        Page<OwnerProfile> raw = propertyGateway.pageOwners(query);
        List<OwnerProfileView> items = raw.items().stream()
                .map(this::toProfileView)
                .toList();
        return new Page<>(items, raw.total(), raw.page(), raw.size());
    }

    /**
     * 业主名册详情：业主聚合视图 + 房产明细列表。
     * uid 不存在返回 {@link Optional#empty()}，由 interfaces 层抛 404。
     */
    public Optional<OwnerDetailView> getOwnerDetail(Long uid, Long tenantId) {
        return propertyGateway.findOwnerProfile(uid, tenantId).map(profile -> {
            List<OwnerPropertyDetail> properties = propertyGateway.listPropertiesByUid(uid, tenantId);
            return new OwnerDetailView(toProfileView(profile), properties);
        });
    }

    private OwnerProfileView toProfileView(OwnerProfile profile) {
        return new OwnerProfileView(
                profile.uid(),
                profile.accountId(),
                profile.phone(),
                nameDecryptor.safeDecrypt(profile.realNameCipher()),
                profile.realNameVerified(),
                profile.authLevel(),
                profile.accountStatus(),
                profile.propertyCount(),
                profile.totalBuildArea(),
                profile.createTime());
    }
}
