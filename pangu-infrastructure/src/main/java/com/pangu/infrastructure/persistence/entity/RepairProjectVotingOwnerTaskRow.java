// 关联业务：映射冻结表决名册中业主本人可见的维修表决任务。
package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.Instant;

@Data
public class RepairProjectVotingOwnerTaskRow {
    private Long projectId;
    private Long planId;
    private String projectNo;
    private String projectName;
    private Long subjectId;
    private Long executionPackageId;
    private Long opid;
    private Long roomId;
    private String collectionMode;
    private String status;
    private String result;
    private String packageHash;
    private Instant voteStartAt;
    private Instant voteEndAt;
}
