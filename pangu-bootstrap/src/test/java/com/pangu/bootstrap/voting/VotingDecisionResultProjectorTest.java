// 关联业务：验证新旧正式表决结果都能形成公开汇总，且不会补造历史缺失的选项统计。
package com.pangu.bootstrap.voting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.application.voting.VotingDecisionResultProjector;
import com.pangu.domain.repository.VotingResultRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class VotingDecisionResultProjectorTest {

    private final VotingDecisionResultProjector projector =
            new VotingDecisionResultProjector(new ObjectMapper());

    @Test
    void projectsCompleteOptionTotalsFromImmutablePayload() {
        VotingDecisionResultProjector.View result = projector.project(snapshot(
                "{\"supportArea\":\"70.00\",\"supportOwnerCount\":2,"
                        + "\"againstArea\":\"20.00\",\"againstOwnerCount\":1,"
                        + "\"abstainArea\":\"10.00\",\"abstainOwnerCount\":1}"));

        assertEquals(new BigDecimal("70.00"), result.supportArea());
        assertEquals(2L, result.supportOwnerCount());
        assertEquals(new BigDecimal("20.00"), result.againstArea());
        assertEquals(1L, result.againstOwnerCount());
        assertEquals(new BigDecimal("10.00"), result.abstainArea());
        assertEquals(1L, result.abstainOwnerCount());
    }

    @Test
    void keepsMissingLegacyOptionTotalsUnknown() {
        VotingDecisionResultProjector.View result = projector.project(snapshot(null));

        assertEquals(new BigDecimal("100.00"), result.participatingArea());
        assertNull(result.supportArea());
        assertNull(result.supportOwnerCount());
        assertNull(result.againstArea());
        assertNull(result.abstainArea());
    }

    private VotingResultRepository.Snapshot snapshot(String resultPayloadJson) {
        return new VotingResultRepository.Snapshot(
                1L, 1,
                new BigDecimal("100.00"), 4L,
                new BigDecimal("100.00"), 4L,
                true, true, resultPayloadJson,
                2L, "attestation", 3L, 4L,
                "proposal", "rule", "package");
    }
}
