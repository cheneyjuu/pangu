// 关联业务：定义社区设置变更记录的稳定操作代码、业务文案和所属配置分区。
package com.pangu.domain.model.community;

import java.util.Arrays;
import java.util.Optional;

/**
 * 社区设置审计操作类型。
 *
 * <p>数据库保存稳定代码，管理端展示中文业务文案，避免把内部事件名直接暴露给业务人员。</p>
 */
public enum CommunitySettingsOperation {

    UPDATE_ORGANIZATION("修改组织备案", "ORGANIZATION"),
    UPDATE_ASSET_LEDGER("修改建筑名册", "BUILDING"),
    RECALCULATE_DENOMINATOR("重新对账并发布计票基数", "DENOMINATOR"),
    SUBMIT_DENOMINATOR_REVIEW("提交计票基数复核", "DENOMINATOR"),
    REVIEW_DENOMINATOR_REQUEST("复核计票基数变更", "DENOMINATOR"),
    UPDATE_RULES("修改自治与财务规则", "RULES");

    private final String label;
    private final String sectionCode;

    CommunitySettingsOperation(String label, String sectionCode) {
        this.label = label;
        this.sectionCode = sectionCode;
    }

    public String code() {
        return name();
    }

    public String label() {
        return label;
    }

    public String sectionCode() {
        return sectionCode;
    }

    public static Optional<CommunitySettingsOperation> fromCode(String code) {
        return Arrays.stream(values())
                .filter(operation -> operation.code().equals(code))
                .findFirst();
    }
}
