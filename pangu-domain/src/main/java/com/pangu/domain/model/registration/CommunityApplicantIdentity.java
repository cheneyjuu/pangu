// 关联业务：表达注册人申报身份，并与审核后的最小权限映射保持分离。
package com.pangu.domain.model.registration;

/**
 * 注册人申报身份。
 *
 * <p>该值只代表申请声明；只有审核通过后才能映射为工作身份或 C 端业主身份。
 */
public enum CommunityApplicantIdentity {
    COMMITTEE_DIRECTOR,
    COMMITTEE_VICE_DIRECTOR,
    COMMITTEE_MEMBER,
    OWNER,
    COMMUNITY_STAFF
}
