package com.pangu.infrastructure.persistence.mapper;

import com.pangu.domain.model.user.WorkIdentityBuildingScope;
import com.pangu.infrastructure.persistence.annotation.DataScope;
import com.pangu.infrastructure.persistence.entity.AssignableUserRow;
import com.pangu.infrastructure.persistence.entity.AssignedBuildingSummaryRow;
import com.pangu.infrastructure.persistence.entity.BuildingAssignmentRow;
import com.pangu.infrastructure.persistence.entity.BuildingOccupantRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 楼栋责任田分配 mapper（{@code sys_user_building} + 关联表）。
 *
 * <p>{@link #selectDistinctBuildings} 挂 {@code @DataScope(buildingAlias="op")} 做行级兜底——
 * ALL_COMMUNITY 分配者看本租户全部楼栋（拦截器放行，靠 SQL 的 tenant_id 过滤），
 * OWNER_GROUP 用户若误入则只看自己授权楼栋。
 */
@Mapper
public interface BuildingAssignmentMapper {

    /** 同租户内某角色用户 + 已生效楼栋数（tenantId=null 时跨租户）。 */
    List<AssignableUserRow> selectUsersByRole(@Param("roleKey") String roleKey,
                                              @Param("tenantId") Long tenantId);

    /**
     * 模糊搜索可分配角色用户。
     *
     * <p>三字段 OR：nick_name ILIKE / phone = / phone LIKE %tail。
     * 受可分配角色（GRID_MEMBER / VOLUNTEER / OWNER_REPRESENTATIVE）固定过滤；
     * tenantId=null 时跨租户。{@code limit} 由 service 层注入硬上限。
     */
    List<AssignableUserRow> searchUsers(@Param("keyword") String keyword,
                                        @Param("tenantId") Long tenantId,
                                        @Param("limit") int limit);

    /**
     * 某楼栋当前所有 status=1 的占用者（含不同角色）。
     */
    List<BuildingOccupantRow> selectOccupantsByBuilding(@Param("buildingId") Long buildingId,
                                                        @Param("tenantId") Long tenantId);

    /** distinct building_id（@DataScope 行级兜底 + 显式 tenant_id 过滤）。 */
    @DataScope(tenantAlias = "op", buildingAlias = "op")
    List<Long> selectDistinctBuildings(@Param("tenantId") Long tenantId);

    /** 某用户已生效楼栋分配（status=1），按 building_id 排序。 */
    List<BuildingAssignmentRow> selectAssignmentsByUser(@Param("userId") Long userId);

    /** 按授权 tenant/building 范围汇总楼栋户数。 */
    List<AssignedBuildingSummaryRow> selectBuildingSummariesByScopes(
            @Param("scopes") List<WorkIdentityBuildingScope> scopes);

    /** 查 (user_id, building_id) 已有记录（含已撤销），用于幂等 assign。 */
    BuildingAssignmentRow selectExisting(@Param("userId") Long userId,
                                         @Param("buildingId") Long buildingId);

    /** 新建分配（status=1）。 */
    int insertAssignment(@Param("userId") Long userId,
                         @Param("buildingId") Long buildingId,
                         @Param("tenantId") Long tenantId,
                         @Param("assignedBy") Long assignedBy);

    /** 已撤销记录复活（status 2→1，刷新 assigned_by/at）。 */
    int reactivateAssignment(@Param("assignmentId") Long assignmentId,
                             @Param("assignedBy") Long assignedBy);

    /** 撤销（status 1→2）。返回实际行数。 */
    int revokeAssignment(@Param("userId") Long userId,
                         @Param("buildingId") Long buildingId,
                         @Param("revokeReason") String revokeReason);

    /** 楼栋是否存在于指定租户的 c_owner_property。 */
    boolean existsBuildingInTenant(@Param("buildingId") Long buildingId,
                                   @Param("tenantId") Long tenantId);

    /** 用户是否持指定角色且属指定租户（tenantId=null 时不校验租户）。 */
    boolean userHasRoleInTenant(@Param("userId") Long userId,
                                @Param("roleKey") String roleKey,
                                @Param("tenantId") Long tenantId);
}
