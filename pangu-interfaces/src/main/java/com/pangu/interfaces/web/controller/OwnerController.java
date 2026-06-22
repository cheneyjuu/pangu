package com.pangu.interfaces.web.controller;

import com.pangu.application.owner.OwnerDetailView;
import com.pangu.application.owner.OwnerProfileView;
import com.pangu.application.owner.OwnerQueryService;
import com.pangu.domain.common.Page;
import com.pangu.domain.gateway.dto.OwnerQuery;
import com.pangu.interfaces.security.SecurityUtils;
import com.pangu.interfaces.web.controller.dto.PageResponse;
import com.pangu.interfaces.web.controller.dto.owner.OwnerDetailResponse;
import com.pangu.interfaces.web.controller.dto.owner.OwnerListResponse;
import com.pangu.interfaces.web.exception.AppException;
import com.pangu.interfaces.web.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 业主名册管理端只读 API（M4 读侧）。
 *
 * <p>权限：
 * <ul>
 *   <li>{@code GET /api/v1/owners} —— {@code owner:list}（分页 + 手机号/楼栋/房号过滤）</li>
 *   <li>{@code GET /api/v1/owners/{uid}} —— {@code owner:detail}（业主聚合 + 房产明细）</li>
 * </ul>
 *
 * <p>租户由 {@link SecurityUtils#getTenantId()} 取得（缺失即 403），行级数据范围由
 * mapper {@code @DataScope(buildingAlias="op")} 注入；service 不做重复 ABAC 校验。
 *
 * <p>姓名走「服务层容错解密」（{@code NameDecryptor.safeDecrypt}）：开发期 MOCK_ 明文回退原值，
 * 生产 hex 密文正常解密——避免 SM4 TypeHandler 硬解密在 mock 数据上抛 500。
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class OwnerController extends BaseController {

    /** 与现有提名搜索同款守卫：手机号前缀至少 3 位，否则降级为「不按手机号过滤」。 */
    private static final int MIN_PHONE_PREFIX_LENGTH = 3;

    /** 列表 page/size 上限，与 {@code SubjectAdminController.page()} 范式一致。 */
    private static final int MAX_PAGE_SIZE = 100;

    private final OwnerQueryService ownerQueryService;

    @GetMapping("/owners")
    @PreAuthorize("hasAuthority('owner:list')")
    public Result<PageResponse<OwnerListResponse>> list(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "phone", required = false) String phone,
            @RequestParam(name = "buildingId", required = false) Long buildingId,
            @RequestParam(name = "roomId", required = false) Long roomId) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        OwnerQuery query = new OwnerQuery(
                requireTenantId(),
                normalizePhonePrefix(phone),
                buildingId,
                roomId,
                safePage,
                safeSize);
        Page<OwnerProfileView> result = ownerQueryService.pageOwners(query);
        return success(PageResponse.from(result, OwnerListResponse::from));
    }

    @GetMapping("/owners/{uid}")
    @PreAuthorize("hasAuthority('owner:detail')")
    public Result<OwnerDetailResponse> detail(@PathVariable("uid") Long uid) {
        OwnerDetailView view = ownerQueryService.getOwnerDetail(uid, requireTenantId())
                .orElseThrow(() -> new AppException(CommonErrorCode.NOT_FOUND, "业主不存在或不在本小区"));
        return success(OwnerDetailResponse.from(view));
    }

    private String normalizePhonePrefix(String phone) {
        if (phone == null) {
            return null;
        }
        String trimmed = phone.trim();
        if (trimmed.isEmpty() || trimmed.length() < MIN_PHONE_PREFIX_LENGTH) {
            return null;
        }
        return trimmed;
    }

    private Long requireTenantId() {
        Long tenantId = SecurityUtils.getTenantId();
        if (tenantId == null) {
            throw new AppException(CommonErrorCode.FORBIDDEN, "未识别到租户上下文，禁止访问该操作");
        }
        return tenantId;
    }
}
