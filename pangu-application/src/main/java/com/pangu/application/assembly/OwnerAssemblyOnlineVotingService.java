// 关联业务：将业主大会公开表决包映射到通用线上实名表决适配器。
package com.pangu.application.assembly;

import com.pangu.application.voting.OnlineVotingException;
import com.pangu.application.voting.OnlineVotingService;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.assembly.OwnersAssemblyPackage;
import com.pangu.domain.model.voting.OnlineBallotSubmission;
import com.pangu.domain.model.voting.OnlinePaperAssistanceRequest;
import com.pangu.domain.model.voting.OnlineVotingAcknowledgement;
import com.pangu.domain.model.voting.VotingExecutionPackage;
import com.pangu.domain.repository.OwnersAssemblyRepository;
import com.pangu.domain.repository.VotingExecutionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

import static com.pangu.application.assembly.OwnersAssemblyApplicationException.Reason.AUTH_LEVEL_INSUFFICIENT;
import static com.pangu.application.assembly.OwnersAssemblyApplicationException.Reason.CONCURRENT_MODIFICATION;
import static com.pangu.application.assembly.OwnersAssemblyApplicationException.Reason.FORBIDDEN;
import static com.pangu.application.assembly.OwnersAssemblyApplicationException.Reason.INVALID_STATUS;
import static com.pangu.application.assembly.OwnersAssemblyApplicationException.Reason.NOT_FOUND;
import static com.pangu.application.assembly.OwnersAssemblyApplicationException.Reason.PARAM_INVALID;
import static com.pangu.application.assembly.OwnersAssemblyApplicationException.Reason.VOTE_ALREADY_CAST;

@Service
@RequiredArgsConstructor
public class OwnerAssemblyOnlineVotingService {

    private final OwnersAssemblyRepository ownersAssemblyRepository;
    private final VotingExecutionRepository votingExecutionRepository;
    private final OnlineVotingService onlineVotingService;
    private final UserContextHolder userContextHolder;

    public OnlineVotingAcknowledgement acknowledge(
            Long legacyPackageId, Long opid, String packageHash, Boolean confirmed) {
        ResolvedPackage resolved = resolveOwnerPackage(legacyPackageId);
        try {
            return onlineVotingService.acknowledge(new OnlineVotingService.AcknowledgeCommand(
                    resolved.execution().getPackageId(), resolved.legacy().tenantId(), opid,
                    packageHash, confirmed, Instant.now()));
        } catch (OnlineVotingException ex) {
            throw translate(ex);
        }
    }

    public OnlineBallotSubmission submit(
            Long legacyPackageId,
            Long opid,
            String packageHash,
            Boolean confirmed,
            String idempotencyKey,
            List<OnlineVotingService.Decision> decisions) {
        ResolvedPackage resolved = resolveOwnerPackage(legacyPackageId);
        try {
            return onlineVotingService.submit(new OnlineVotingService.SubmitCommand(
                    resolved.execution().getPackageId(), resolved.legacy().tenantId(), opid,
                    packageHash, confirmed, idempotencyKey, decisions, Instant.now()));
        } catch (OnlineVotingException ex) {
            throw translate(ex);
        }
    }

    public OnlinePaperAssistanceRequest requestPaperAssistance(
            Long legacyPackageId, Long opid, String packageHash) {
        ResolvedPackage resolved = resolveOwnerPackage(legacyPackageId);
        try {
            return onlineVotingService.requestPaperAssistance(new OnlineVotingService.PaperAssistanceCommand(
                    resolved.execution().getPackageId(), resolved.legacy().tenantId(), opid,
                    packageHash, Instant.now()));
        } catch (OnlineVotingException ex) {
            throw translate(ex);
        }
    }

    public OnlinePaperAssistanceRequest withdrawPaperAssistance(
            Long legacyPackageId, Long requestId, Long opid, String packageHash) {
        ResolvedPackage resolved = resolveOwnerPackage(legacyPackageId);
        try {
            return onlineVotingService.withdrawPaperAssistance(
                    resolved.execution().getPackageId(), requestId, resolved.legacy().tenantId(), opid,
                    packageHash, Instant.now());
        } catch (OnlineVotingException ex) {
            throw translate(ex);
        }
    }

    private ResolvedPackage resolveOwnerPackage(Long legacyPackageId) {
        UserContext owner = userContextHolder.current();
        if (owner == null || !owner.isCUser() || owner.tenantId() == null) {
            throw new OwnersAssemblyApplicationException(FORBIDDEN, "未识别到当前小区业主身份");
        }
        OwnersAssemblyPackage legacy = ownersAssemblyRepository.findPackage(legacyPackageId, owner.tenantId())
                .filter(candidate -> "VOTING".equals(candidate.status()))
                .orElseThrow(() -> new OwnersAssemblyApplicationException(
                        NOT_FOUND, "本次业主大会尚未开始表决或已经结束"));
        List<Long> subjectIds = ownersAssemblyRepository.listSubjectIds(legacy.packageId(), legacy.tenantId());
        if (subjectIds.isEmpty()) {
            throw new OwnersAssemblyApplicationException(INVALID_STATUS, "本次业主大会没有正式表决事项");
        }
        VotingExecutionPackage execution = votingExecutionRepository.findPackageBySubjectId(subjectIds.getFirst())
                .filter(candidate -> candidate.getBusinessType()
                        == VotingExecutionPackage.BusinessType.OWNERS_ASSEMBLY)
                .filter(candidate -> legacy.packageId().equals(candidate.getBusinessReferenceId()))
                .orElseThrow(() -> new OwnersAssemblyApplicationException(
                        INVALID_STATUS, "本次业主大会尚未建立统一收票记录"));
        return new ResolvedPackage(legacy, execution);
    }

    private OwnersAssemblyApplicationException translate(OnlineVotingException failure) {
        OwnersAssemblyApplicationException.Reason reason = switch (failure.getReason()) {
            case NOT_FOUND -> NOT_FOUND;
            case FORBIDDEN -> FORBIDDEN;
            case AUTHENTICATION_REQUIRED -> AUTH_LEVEL_INSUFFICIENT;
            case INVALID_STATUS -> INVALID_STATUS;
            case INVALID_ARGUMENT -> PARAM_INVALID;
            case ALREADY_SUBMITTED -> VOTE_ALREADY_CAST;
            case CONCURRENT_MODIFICATION -> CONCURRENT_MODIFICATION;
        };
        return new OwnersAssemblyApplicationException(reason, failure.getMessage(), failure);
    }

    private record ResolvedPackage(OwnersAssemblyPackage legacy, VotingExecutionPackage execution) {
    }
}
