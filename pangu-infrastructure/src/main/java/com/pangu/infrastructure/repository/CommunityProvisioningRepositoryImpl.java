// 关联业务：在审核通过事务中创建租户、初始化组织、冷启动工作区和经核验身份。
package com.pangu.infrastructure.repository;

import com.pangu.domain.context.UserContext;
import com.pangu.domain.model.registration.CommunityApplicantIdentity;
import com.pangu.domain.model.registration.CommunityRegistrationApplication;
import com.pangu.domain.model.registration.CommunityRegistrationReviewMode;
import com.pangu.domain.repository.CommunityProvisioningRepository;
import com.pangu.infrastructure.persistence.mapper.CommunityRegistrationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

import java.util.Locale;

/**
 * 小区冷启动开通仓储实现。
 */
@Repository
@RequiredArgsConstructor
public class CommunityProvisioningRepositoryImpl implements CommunityProvisioningRepository {

    private static final int INITIALIZATION_DEPT_TYPE = 12;
    private static final int COMMITTEE_DEPT_TYPE = 4;

    private final CommunityRegistrationMapper mapper;

    @Override
    public ProvisioningResult provision(
            CommunityRegistrationApplication application,
            UserContext reviewer,
            CommunityRegistrationReviewMode reviewMode) {
        CommunityRegistrationMapper.DeptSourceRow parent = mapper.selectDeptSource(reviewer.deptId());
        if (parent == null || parent.getDeptType() == null || parent.getDeptType() != 1
                || !"G".equals(parent.getDeptCategory())) {
            throw new ProvisioningConsistencyException("审核身份未挂在有效街镇或平台审核根组织");
        }
        try {
            Long tenantId = mapper.nextTenantId();
            insertTenant(application, parent, reviewMode, tenantId);
            CommunityRegistrationMapper.DeptInsertRow initializationDept = insertDept(
                    parent.getDeptId(), childAncestors(parent.getAncestors(), parent.getDeptId()),
                    application.communityName() + "初始化工作区", INITIALIZATION_DEPT_TYPE, "G", tenantId, 1);

            Long committeeDeptId = null;
            Long applicantWorkUserId = null;
            if (isCommitteeIdentity(application.claimedIdentity())) {
                CommunityRegistrationMapper.DeptInsertRow committeeDept = insertDept(
                        initializationDept.getDeptId(),
                        childAncestors(initializationDept.getAncestors(), initializationDept.getDeptId()),
                        application.communityName() + "业主委员会",
                        COMMITTEE_DEPT_TYPE, "B", tenantId, 10);
                committeeDeptId = committeeDept.getDeptId();
                applicantWorkUserId = createCommitteeIdentity(application, reviewer, tenantId, committeeDeptId);
            } else if (application.claimedIdentity() == CommunityApplicantIdentity.COMMUNITY_STAFF) {
                applicantWorkUserId = mapper.selectExistingGovernmentUser(
                        application.applicantAccountId(), reviewer.deptId());
                if (applicantWorkUserId == null) {
                    throw new ProvisioningConsistencyException(
                            "居委会工作人员申请必须关联审核人所属街镇范围内的既有 G 端工作身份");
                }
            }

            String affiliationStatus = reviewMode == CommunityRegistrationReviewMode.STREET
                    ? "STREET_CONFIRMED"
                    : "PLATFORM_REVIEWED_PENDING_STREET_CONFIRMATION";
            CommunityRegistrationMapper.OnboardingInsertRow onboarding =
                    new CommunityRegistrationMapper.OnboardingInsertRow();
            onboarding.setApplicationId(application.applicationId());
            onboarding.setTenantId(tenantId);
            onboarding.setOfficialAffiliationStatus(affiliationStatus);
            onboarding.setInitializationDeptId(initializationDept.getDeptId());
            onboarding.setCommitteeDeptId(committeeDeptId);
            onboarding.setApplicantWorkUserId(applicantWorkUserId);
            onboarding.setCreatedByUserId(reviewer.userId());
            mapper.insertOnboarding(onboarding);
            return new ProvisioningResult(
                    tenantId, initializationDept.getDeptId(), committeeDeptId,
                    applicantWorkUserId, affiliationStatus);
        } catch (ProvisioningConsistencyException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            throw new ProvisioningConsistencyException("小区租户开通被数据库一致性规则拒绝", ex);
        }
    }

    private void insertTenant(
            CommunityRegistrationApplication application,
            CommunityRegistrationMapper.DeptSourceRow parent,
            CommunityRegistrationReviewMode reviewMode,
            Long tenantId) {
        boolean committeeEstablished = isCommitteeIdentity(application.claimedIdentity());
        CommunityRegistrationMapper.TenantProvisionRow tenant =
                new CommunityRegistrationMapper.TenantProvisionRow();
        tenant.setTenantId(tenantId);
        tenant.setTenantCode("COMM-" + tenantId);
        tenant.setTenantShortCode("C" + tenantId);
        tenant.setTenantName(application.communityName());
        tenant.setPropertyAreaName(application.communityName());
        tenant.setProvinceCode(application.provinceCode());
        tenant.setProvinceName(application.provinceName());
        tenant.setCityCode(application.cityCode());
        tenant.setCityName(application.cityName());
        tenant.setDistrictCode(application.districtCode());
        tenant.setDistrictName(application.districtName());
        tenant.setStreetName(reviewMode == CommunityRegistrationReviewMode.STREET ? parent.getDeptName() : null);
        tenant.setAddress(application.communityAddress());
        tenant.setPlannedHouseholdCount(application.declaredHouseholdCount());
        tenant.setOwnersAssemblyEstablished(committeeEstablished ? 1 : 0);
        tenant.setCommitteeEstablished(committeeEstablished ? 1 : 0);
        tenant.setRuleConfigId(mapper.selectDefaultGovernancePolicyId());
        tenant.setGovernanceStatus("HANDOVER_LOCK");
        tenant.setRegistrationFingerprint(application.communityFingerprint());
        mapper.insertTenant(tenant);
    }

    private CommunityRegistrationMapper.DeptInsertRow insertDept(
            Long parentId,
            String ancestors,
            String deptName,
            int deptType,
            String deptCategory,
            Long tenantId,
            int orderNum) {
        CommunityRegistrationMapper.DeptInsertRow dept = new CommunityRegistrationMapper.DeptInsertRow();
        dept.setParentId(parentId);
        dept.setAncestors(ancestors);
        dept.setDeptName(deptName);
        dept.setDeptType(deptType);
        dept.setDeptCategory(deptCategory);
        dept.setTenantId(tenantId);
        dept.setOrderNum(orderNum);
        mapper.insertDept(dept);
        return dept;
    }

    private Long createCommitteeIdentity(
            CommunityRegistrationApplication application,
            UserContext reviewer,
            Long tenantId,
            Long committeeDeptId) {
        RoleAssignment assignment = roleAssignment(application.claimedIdentity());
        CommunityRegistrationMapper.RoleRow role = mapper.selectRoleByKey(assignment.roleKey());
        if (role == null) {
            throw new ProvisioningConsistencyException("预置工作角色不存在：" + assignment.roleKey());
        }
        CommunityRegistrationMapper.SysUserInsertRow user = new CommunityRegistrationMapper.SysUserInsertRow();
        user.setAccountId(application.applicantAccountId());
        user.setDeptId(committeeDeptId);
        user.setUserName(("reg_" + application.applicantAccountId() + "_" + tenantId)
                .toLowerCase(Locale.ROOT));
        user.setNickName(application.applicantName());
        mapper.insertSysUser(user);
        String effectiveScope = role.getFixedDataScope() == null
                ? role.getDefaultDataScope()
                : role.getFixedDataScope();
        mapper.insertSysUserRole(user.getUserId(), role.getRoleId(), effectiveScope, reviewer.userId());
        mapper.insertCommitteePosition(tenantId, user.getUserId(), assignment.position());
        return user.getUserId();
    }

    private RoleAssignment roleAssignment(CommunityApplicantIdentity identity) {
        return switch (identity) {
            case COMMITTEE_DIRECTOR -> new RoleAssignment("COMMITTEE_DIRECTOR", "DIRECTOR");
            case COMMITTEE_VICE_DIRECTOR -> new RoleAssignment("COMMITTEE_MEMBER", "VICE_DIRECTOR");
            case COMMITTEE_MEMBER -> new RoleAssignment("COMMITTEE_MEMBER", "MEMBER");
            default -> throw new ProvisioningConsistencyException("该申报身份不应创建业委会工作身份：" + identity);
        };
    }

    private boolean isCommitteeIdentity(CommunityApplicantIdentity identity) {
        return identity == CommunityApplicantIdentity.COMMITTEE_DIRECTOR
                || identity == CommunityApplicantIdentity.COMMITTEE_VICE_DIRECTOR
                || identity == CommunityApplicantIdentity.COMMITTEE_MEMBER;
    }

    private String childAncestors(String ancestors, Long parentId) {
        return ancestors == null || ancestors.isBlank()
                ? String.valueOf(parentId)
                : ancestors + "," + parentId;
    }

    private record RoleAssignment(String roleKey, String position) {
    }
}
