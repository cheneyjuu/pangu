// 关联业务：在正式表决请求被跨渠道有效票拒绝时，独立保存不含票面选择的冲突审计。
package com.pangu.application.voting;

import com.pangu.domain.context.UserContext;
import com.pangu.domain.model.voting.VotingBallotRecord;
import com.pangu.domain.model.voting.VotingElectorateSnapshot;
import com.pangu.domain.model.voting.VotingExecutionPackage;
import com.pangu.domain.repository.VotingExecutionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/** 冲突审计独立提交，避免随后抛出的业务冲突把留痕一并回滚。 */
@Service
@RequiredArgsConstructor
public class VotingConflictAuditService {

    private final VotingExecutionRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRejectedOnlineBallot(
            VotingExecutionPackage ballotPackage,
            VotingElectorateSnapshot.Item electorate,
            UserContext owner,
            String choiceManifestHash,
            Instant occurredAt) {
        List<Long> conflictingBallotIds = repository
                .listSubjectIds(ballotPackage.getPackageId(), ballotPackage.getTenantId()).stream()
                .map(subjectId -> repository.findActiveBallot(
                        subjectId, electorate.snapshotItemId(), ballotPackage.getTenantId()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .map(VotingBallotRecord::ballotId)
                .toList();
        String ids = conflictingBallotIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        String detail = "{\"attemptedChannel\":\"ONLINE\",\"opid\":" + electorate.representativeOpid()
                + ",\"uid\":" + owner.uid()
                + ",\"accountId\":" + owner.accountId()
                + ",\"choiceManifestHash\":\"" + choiceManifestHash
                + "\",\"conflictingBallotIds\":[" + ids + "]}";
        repository.insertAudit(
                ballotPackage.getPackageId(), ballotPackage.getTenantId(), "ONLINE_BALLOT_CONFLICT",
                ballotPackage.getStatus().name(), ballotPackage.getStatus().name(), null, detail, occurredAt);
    }
}
