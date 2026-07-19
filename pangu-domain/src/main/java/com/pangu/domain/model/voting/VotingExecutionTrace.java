// 关联业务：把表决结果追溯到当时冻结的表决包、名册、方案和规则摘要。
package com.pangu.domain.model.voting;

/** 正式表决结算依据的最小不可变追溯信息。 */
public record VotingExecutionTrace(
        Long executionPackageId,
        Long electorateSnapshotId,
        String proposalSnapshotHash,
        String ruleSnapshotHash,
        String executionPackageHash
) {

    public VotingExecutionTrace {
        if (executionPackageId == null || electorateSnapshotId == null) {
            throw new IllegalArgumentException("正式表决结果必须关联表决包和冻结名册");
        }
        requireSha256(proposalSnapshotHash, "proposalSnapshotHash");
        requireSha256(ruleSnapshotHash, "ruleSnapshotHash");
        requireSha256(executionPackageHash, "executionPackageHash");
    }

    private static void requireSha256(String value, String field) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " 必须为 64 位 SHA-256 摘要");
        }
    }
}
