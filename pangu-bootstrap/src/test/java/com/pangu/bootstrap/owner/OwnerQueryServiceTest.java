package com.pangu.bootstrap.owner;

import com.pangu.application.owner.OwnerDetailView;
import com.pangu.application.owner.OwnerProfileView;
import com.pangu.application.owner.OwnerQueryService;
import com.pangu.domain.common.Page;
import com.pangu.domain.gateway.PropertyGateway;
import com.pangu.domain.gateway.dto.OwnerQuery;
import com.pangu.domain.model.asset.OwnerProfile;
import com.pangu.domain.model.asset.OwnerPropertyDetail;
import com.pangu.domain.security.NameDecryptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@link OwnerQueryService} 单元测试（Mockito）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>{@code pageOwners} 透传 PropertyGateway 行，姓名走 NameDecryptor，元信息保 page/size/total；</li>
 *   <li>{@code getOwnerDetail} uid 不存在 → {@link Optional#empty()}；</li>
 *   <li>{@code getOwnerDetail} 命中 → 含房产明细；</li>
 *   <li>姓名容错：解密器返回原值时（mock 明文场景）service 直接透传，不抛。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class OwnerQueryServiceTest {

    private static final Long TENANT = 10001L;
    private static final Long UID = 70001L;

    @Mock
    private PropertyGateway propertyGateway;

    @Mock
    private NameDecryptor nameDecryptor;

    private OwnerQueryService service() {
        return new OwnerQueryService(propertyGateway, nameDecryptor);
    }

    @Test
    void pageOwners_passes_through_paging_and_decrypts_name() {
        OwnerProfile raw = new OwnerProfile(
                UID, 50001L, "13800000012", "CIPHER_HEX_ZHANG", 1, 3, 1,
                2, new BigDecimal("180.50"),
                LocalDateTime.parse("2025-01-15T10:00:00"));
        Page<OwnerProfile> rawPage = new Page<>(List.of(raw), 1L, 1, 20);
        when(propertyGateway.pageOwners(any(OwnerQuery.class))).thenReturn(rawPage);
        when(nameDecryptor.safeDecrypt("CIPHER_HEX_ZHANG")).thenReturn("张三");

        OwnerQuery query = new OwnerQuery(TENANT, "138", null, null, 1, 20);
        Page<OwnerProfileView> view = service().pageOwners(query);

        assertEquals(1L, view.total());
        assertEquals(1, view.page());
        assertEquals(20, view.size());
        assertEquals(1, view.items().size());
        OwnerProfileView item = view.items().get(0);
        assertEquals(UID, item.uid());
        assertEquals("张三", item.realName());
        assertEquals("13800000012", item.phone());
        assertEquals(2, item.propertyCount());
        assertEquals(new BigDecimal("180.50"), item.totalBuildArea());
    }

    @Test
    void pageOwners_falls_back_to_original_when_decryptor_returns_input() {
        // 模拟 mock 明文场景：解密器对非 hex 输入回退到原值
        OwnerProfile raw = new OwnerProfile(
                UID, 50001L, "13800000012", "MOCK_张三", 0, 1, 1, 1,
                new BigDecimal("99.00"),
                LocalDateTime.parse("2025-01-15T10:00:00"));
        when(propertyGateway.pageOwners(any(OwnerQuery.class)))
                .thenReturn(new Page<>(List.of(raw), 1L, 1, 20));
        when(nameDecryptor.safeDecrypt("MOCK_张三")).thenReturn("MOCK_张三");

        Page<OwnerProfileView> view = service().pageOwners(
                new OwnerQuery(TENANT, null, null, null, 1, 20));

        assertEquals("MOCK_张三", view.items().get(0).realName());
    }

    @Test
    void getOwnerDetail_returns_empty_when_uid_missing() {
        when(propertyGateway.findOwnerProfile(UID, TENANT)).thenReturn(Optional.empty());

        Optional<OwnerDetailView> result = service().getOwnerDetail(UID, TENANT);

        assertTrue(result.isEmpty());
    }

    @Test
    void getOwnerDetail_aggregates_profile_and_properties() {
        OwnerProfile profile = new OwnerProfile(
                UID, 50001L, "13800000012", "CIPHER_X", 1, 3, 1, 2,
                new BigDecimal("180.50"),
                LocalDateTime.parse("2025-01-15T10:00:00"));
        OwnerPropertyDetail prop = new OwnerPropertyDetail(
                900001L, 30001L, 30001101L, new BigDecimal("90.25"), true, 1);
        when(propertyGateway.findOwnerProfile(UID, TENANT)).thenReturn(Optional.of(profile));
        when(propertyGateway.listPropertiesByUid(UID, TENANT)).thenReturn(List.of(prop));
        when(nameDecryptor.safeDecrypt("CIPHER_X")).thenReturn("张三");

        OwnerDetailView detail = service().getOwnerDetail(UID, TENANT).orElseThrow();

        assertEquals(UID, detail.profile().uid());
        assertEquals("张三", detail.profile().realName());
        assertEquals(1, detail.properties().size());
        assertSame(prop, detail.properties().get(0));
    }
}
