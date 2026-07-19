// 关联业务：执行纸质表决送达、回收、录入复核和逐事项结果的数据库读写。
package com.pangu.infrastructure.persistence.mapper;

import com.pangu.infrastructure.persistence.entity.PaperBallotEntryItemRow;
import com.pangu.infrastructure.persistence.entity.PaperBallotEntryRow;
import com.pangu.infrastructure.persistence.entity.PaperBallotOutcomeRow;
import com.pangu.infrastructure.persistence.entity.PaperBallotRow;
import com.pangu.infrastructure.persistence.entity.PaperVotingDeliveryRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

@Mapper
public interface PaperVotingMapper {

    int insertDelivery(PaperVotingDeliveryRow row);

    PaperVotingDeliveryRow selectDelivery(@Param("paperDeliveryId") Long paperDeliveryId,
                                           @Param("packageId") Long packageId,
                                           @Param("tenantId") Long tenantId);

    PaperVotingDeliveryRow selectDeliveryForUpdate(@Param("paperDeliveryId") Long paperDeliveryId,
                                                    @Param("packageId") Long packageId,
                                                    @Param("tenantId") Long tenantId);

    int confirmDelivery(@Param("paperDeliveryId") Long paperDeliveryId,
                        @Param("tenantId") Long tenantId,
                        @Param("reviewedByUserId") Long reviewedByUserId,
                        @Param("reviewedAt") Instant reviewedAt,
                        @Param("unifiedDeliveryId") Long unifiedDeliveryId,
                        @Param("expectedVersion") Long expectedVersion);

    int rejectDelivery(@Param("paperDeliveryId") Long paperDeliveryId,
                       @Param("tenantId") Long tenantId,
                       @Param("reviewedByUserId") Long reviewedByUserId,
                       @Param("reviewedAt") Instant reviewedAt,
                       @Param("reviewNote") String reviewNote,
                       @Param("expectedVersion") Long expectedVersion);

    int insertBallot(PaperBallotRow row);

    PaperBallotRow selectBallot(@Param("paperBallotId") Long paperBallotId,
                                @Param("packageId") Long packageId,
                                @Param("tenantId") Long tenantId);

    PaperBallotRow selectBallotForUpdate(@Param("paperBallotId") Long paperBallotId,
                                         @Param("packageId") Long packageId,
                                         @Param("tenantId") Long tenantId);

    int insertEntry(PaperBallotEntryRow row);

    int insertEntryItems(@Param("entryId") Long entryId,
                         @Param("items") List<PaperBallotEntryItemRow> items);

    PaperBallotEntryRow selectEntry(@Param("entryId") Long entryId,
                                    @Param("paperBallotId") Long paperBallotId,
                                    @Param("tenantId") Long tenantId);

    PaperBallotEntryRow selectEntryForUpdate(@Param("entryId") Long entryId,
                                             @Param("paperBallotId") Long paperBallotId,
                                             @Param("tenantId") Long tenantId);

    PaperBallotEntryRow selectLatestEntry(@Param("paperBallotId") Long paperBallotId,
                                          @Param("tenantId") Long tenantId);

    List<PaperBallotEntryItemRow> selectEntryItems(@Param("entryId") Long entryId);

    int selectNextEntryVersion(@Param("paperBallotId") Long paperBallotId,
                               @Param("tenantId") Long tenantId);

    int confirmEntry(@Param("entryId") Long entryId,
                     @Param("tenantId") Long tenantId,
                     @Param("reviewedByUserId") Long reviewedByUserId,
                     @Param("reviewedAt") Instant reviewedAt);

    int rejectEntry(@Param("entryId") Long entryId,
                    @Param("tenantId") Long tenantId,
                    @Param("reviewedByUserId") Long reviewedByUserId,
                    @Param("reviewedAt") Instant reviewedAt,
                    @Param("reviewNote") String reviewNote);

    int insertOutcome(PaperBallotOutcomeRow row);

    List<PaperBallotOutcomeRow> selectOutcomes(@Param("paperBallotId") Long paperBallotId,
                                               @Param("tenantId") Long tenantId);

    int markBallotInEntry(@Param("paperBallotId") Long paperBallotId,
                          @Param("tenantId") Long tenantId,
                          @Param("expectedVersion") Long expectedVersion);

    int markBallotCompleted(@Param("paperBallotId") Long paperBallotId,
                            @Param("tenantId") Long tenantId);

    int voidBallot(@Param("paperBallotId") Long paperBallotId,
                   @Param("tenantId") Long tenantId,
                   @Param("voidedByUserId") Long voidedByUserId,
                   @Param("voidedAt") Instant voidedAt,
                   @Param("voidReason") String voidReason,
                   @Param("expectedVersion") Long expectedVersion);

    List<PaperVotingDeliveryRow> selectDeliveries(@Param("packageId") Long packageId,
                                                  @Param("tenantId") Long tenantId);

    List<PaperBallotRow> selectBallots(@Param("packageId") Long packageId,
                                      @Param("tenantId") Long tenantId);
}
