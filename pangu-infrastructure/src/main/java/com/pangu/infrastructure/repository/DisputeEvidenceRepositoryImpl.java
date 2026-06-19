package com.pangu.infrastructure.repository;

import com.pangu.domain.model.dispute.DisputeEvidence;
import com.pangu.domain.model.dispute.EvidenceKind;
import com.pangu.domain.repository.DisputeEvidenceRepository;
import com.pangu.infrastructure.persistence.entity.DisputeEvidenceRow;
import com.pangu.infrastructure.persistence.mapper.DisputeEvidenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class DisputeEvidenceRepositoryImpl implements DisputeEvidenceRepository {

    private final DisputeEvidenceMapper mapper;

    @Override
    public DisputeEvidence insert(DisputeEvidence evidence) {
        DisputeEvidenceRow row = toRow(evidence);
        mapper.insert(row);
        return new DisputeEvidence(row.getEvidenceId(), evidence.disputeId(),
                evidence.kind(), evidence.contentUrl(), evidence.description(),
                evidence.uploadedAt());
    }

    @Override
    public List<DisputeEvidence> findByDisputeId(Long disputeId) {
        return mapper.selectByDisputeId(disputeId).stream()
                .map(this::toValueObject).toList();
    }

    private DisputeEvidenceRow toRow(DisputeEvidence e) {
        DisputeEvidenceRow r = new DisputeEvidenceRow();
        r.setEvidenceId(e.evidenceId());
        r.setDisputeId(e.disputeId());
        r.setEvidenceKind(e.kind().name());
        r.setContentUrl(e.contentUrl());
        r.setDescription(e.description());
        r.setUploadedAt(e.uploadedAt());
        return r;
    }

    private DisputeEvidence toValueObject(DisputeEvidenceRow r) {
        return new DisputeEvidence(r.getEvidenceId(), r.getDisputeId(),
                EvidenceKind.valueOf(r.getEvidenceKind()),
                r.getContentUrl(), r.getDescription(), r.getUploadedAt());
    }
}
