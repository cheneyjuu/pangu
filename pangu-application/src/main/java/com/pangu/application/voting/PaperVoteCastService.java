// 关联业务：在独立事务中把一项已复核纸票写入统一有效票台账，失败时不回滚纸票原始复核记录。
package com.pangu.application.voting;

import com.pangu.domain.model.voting.PaperBallot;
import com.pangu.domain.model.voting.PaperBallotEntry;
import com.pangu.domain.model.voting.VoteChannel;
import com.pangu.domain.model.voting.VotingBallotRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaperVoteCastService {

    private final VotingExecutionService votingExecutionService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public VotingBallotRecord cast(PaperBallot ballot,
                                   PaperBallotEntry.Item item,
                                   Long recordedByUserId) {
        return votingExecutionService.castRecord(new VotingExecutionService.CastBallotCommand(
                ballot.packageId(), item.subjectId(), ballot.tenantId(), ballot.opid(), null,
                item.choice(), VoteChannel.PAPER, ballot.materialHash(), null,
                recordedByUserId, ballot.receivedAt()));
    }
}
