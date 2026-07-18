// 关联业务：验证维修方案正文图片短期凭证的签名、资源绑定和过期边界。
package com.pangu.application.repair;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RepairNarrativeImageDeliveryTicketCodecTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final Instant NOW = Instant.parse("2026-07-18T00:00:00Z");

    @Test
    void issuesSingleParameterUrlAndVerifiesBoundClaims() {
        RepairNarrativeImageDeliveryTicketCodec codec = codec(NOW);

        RepairNarrativeImageDeliveryTicketCodec.DeliveryTicket issued =
                codec.issue(3L, 11L, 20000L);
        URI uri = URI.create(issued.url());
        String token = uri.getRawQuery().substring("ticket=".length());

        assertEquals("pangu.example", uri.getHost());
        assertEquals("/api/v1/public/repair-plan-images/3", uri.getRawPath());
        assertFalse(uri.getRawQuery().contains("&"));
        RepairNarrativeImageDeliveryTicketCodec.TicketClaims claims = codec.verify(3L, token);
        assertEquals(11L, claims.planId());
        assertEquals(20000L, claims.tenantId());
        assertEquals(NOW.plus(RepairNarrativeImageDeliveryTicketCodec.TICKET_VALIDITY),
                claims.expiresAt());
    }

    @Test
    void rejectsTamperedResourceAndExpiredTicket() {
        RepairNarrativeImageDeliveryTicketCodec codec = codec(NOW);
        URI uri = URI.create(codec.issue(3L, 11L, 20000L).url());
        String token = uri.getRawQuery().substring("ticket=".length());

        assertThrows(RepairWorkOrderApplicationException.class, () -> codec.verify(4L, token));
        assertThrows(RepairWorkOrderApplicationException.class,
                () -> codec(NOW.plusSeconds(601)).verify(3L, token));
    }

    private RepairNarrativeImageDeliveryTicketCodec codec(Instant now) {
        return new RepairNarrativeImageDeliveryTicketCodec(
                SECRET,
                "https://pangu.example/api/v1",
                Clock.fixed(now, ZoneOffset.UTC));
    }
}
