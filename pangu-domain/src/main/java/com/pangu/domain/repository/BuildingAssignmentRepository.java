package com.pangu.domain.repository;

import com.pangu.domain.model.user.AssignableUser;
import com.pangu.domain.model.user.BuildingAssignment;

import java.util.List;

/**
 * 楼栋责任田分配仓储端口（Hexagonal Port）— 维护 {@code sys_user_building}。
 *
 * <p>实现位置：{@code pangu-infrastructure/.../repository/BuildingAssignmentRepositoryImpl}。
 *
 * <p>设计原则：
 * <ul>
 *   <li>读侧 {@link #listUsersByRole} / {@link #listBuildings} / {@link #listAssignmentsByUser}
 *       为纯查询；租户隔离由调用方（application 层从 SecurityUtils 注入 tenantId）保证，
 *       {@code listBuildings} 实现侧 mapper 挂 {@code @DataScope} 做行级兜底。</li>
 *   <li>写侧 {@link #assign} 幂等——已生效返回 1（noop）/ 已撤销则复活 / 否则插入；
 *       {@link #revoke} 把 status 1→2，不存在返回 0。</li>
 *   <li>校验类查询 {@link #buildingExistsInTenant} / {@link #userHasAssignableRole}
 *       供 application 层在 assign 前做数据范围 + 角色收口。</li>
 * </ul>
 */
public interface BuildingAssignmentRepository {

    /**
     * 同租户内某角色的可分配用户列表（附已生效楼栋数）。
     *
     * @param roleKey  可分配角色（GRID_OPERATOR / VOLUNTEER / OWNER_REPRESENTATIVE）
     * @param tenantId 租户；街道超管为 {@code null} 时跨租户
     */
    List<AssignableUser> listUsersByRole(String roleKey, Long tenantId);

    /**
     * 可分配楼栋列表（distinct building_id）。
     *
     * @param tenantId 租户；{@code null} 时跨租户
     */
    List<Long> listBuildings(Long tenantId);

    /** 某用户已生效的楼栋分配列表（status=1）。 */
    List<BuildingAssignment> listAssignmentsByUser(Long userId);

    /**
     * 幂等分配楼栋：已生效→noop 返回 1；已撤销→复活返回 1；否则插入返回 1。
     *
     * @return 总是 1（幂等成功）；调用方据此判定
     */
    int assign(Long userId, Long buildingId, Long tenantId, Long assignedBy);

    /**
     * 撤销楼栋分配（status 1→2）。
     *
     * @return 实际撤销行数（0=本无生效授予）
     */
    int revoke(Long userId, Long buildingId);

    /** 楼栋是否存在于指定租户的 c_owner_property（街道超管 tenantId=null 时跳过校验，返回 true）。 */
    boolean buildingExistsInTenant(Long buildingId, Long tenantId);

    /** 目标用户是否持指定可分配角色且属于指定租户（tenantId=null 时不校验租户）。 */
    boolean userHasAssignableRole(Long userId, String roleKey, Long tenantId);
}
