// 关联业务：定义纸质表决送达、回收、录入复核和逐事项结果的持久化端口。
package com.pangu.domain.repository;

import com.pangu.domain.model.voting.PaperBallot;
import com.pangu.domain.model.voting.PaperBallotEntry;
import com.pangu.domain.model.voting.PaperBallotOutcome;
import com.pangu.domain.model.voting.PaperVotingDelivery;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaperVotingRepository {

    PaperVotingDelivery insertDelivery(PaperVotingDelivery delivery);

    Optional<PaperVotingDelivery> findDelivery(Long paperDeliveryId, Long packageId, Long tenantId);

    Optional<PaperVotingDelivery> findDeliveryForUpdate(Long paperDeliveryId, Long packageId, Long tenantId);

    int confirmDelivery(Long paperDeliveryId,
                        Long tenantId,
                        Long reviewedByUserId,
                        Instant reviewedAt,
                        Long unifiedDeliveryId,
                        Long expectedVersion);

    int rejectDelivery(Long paperDeliveryId,
                       Long tenantId,
                       Long reviewedByUserId,
                       Instant reviewedAt,
                       String reviewNote,
                       Long expectedVersion);

    PaperBallot insertBallot(PaperBallot ballot);

    Optional<PaperBallot> findBallot(Long paperBallotId, Long packageId, Long tenantId);

    Optional<PaperBallot> findBallotForUpdate(Long paperBallotId, Long packageId, Long tenantId);

    PaperBallotEntry insertEntry(PaperBallotEntry entry);

    Optional<PaperBallotEntry> findEntry(Long entryId, Long paperBallotId, Long tenantId);

    Optional<PaperBallotEntry> findEntryForUpdate(Long entryId, Long paperBallotId, Long tenantId);

    Optional<PaperBallotEntry> findLatestEntry(Long paperBallotId, Long tenantId);

    int nextEntryVersion(Long paperBallotId, Long tenantId);

    int confirmEntry(Long entryId, Long tenantId, Long reviewedByUserId, Instant reviewedAt);

    int rejectEntry(Long entryId,
                    Long tenantId,
                    Long reviewedByUserId,
                    Instant reviewedAt,
                    String reviewNote);

    void insertOutcome(PaperBallotOutcome outcome);

    List<PaperBallotOutcome> listOutcomes(Long paperBallotId, Long tenantId);

    int markBallotInEntry(Long paperBallotId, Long tenantId, Long expectedVersion);

    int markBallotCompleted(Long paperBallotId, Long tenantId);

    int voidBallot(Long paperBallotId,
                   Long tenantId,
                   Long voidedByUserId,
                   Instant voidedAt,
                   String voidReason,
                   Long expectedVersion);

    List<PaperVotingDelivery> listDeliveries(Long packageId, Long tenantId);

    List<PaperBallot> listBallots(Long packageId, Long tenantId);
}
