package com.pangu.infrastructure.repository;

import com.pangu.domain.model.disclosure.FundLedgerSnapshotData;
import com.pangu.domain.repository.FundLedgerQueryGateway;
import com.pangu.infrastructure.persistence.mapper.FundLedgerQueryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link FundLedgerQueryGateway} 默认实现：解析 period 时间窗 + 调 mapper。
 *
 * <p>时区固定 {@link #ZONE_SH}（Asia/Shanghai, UTC+8），与 t_fund_ledger_entry.occurred_at
 * （{@code TIMESTAMP} 无时区列）的写入习惯对齐——业务侧 entry 全部以本地时间落库，
 * 这里查询窗口也按本地时间换算成 {@link Instant} 下推到 SQL。
 */
@Repository
@RequiredArgsConstructor
public class FundLedgerQueryGatewayImpl implements FundLedgerQueryGateway {

    /** Asia/Shanghai；与 PRD 业务约定一致，避免 UTC 错位一天。 */
    static final ZoneId ZONE_SH = ZoneId.of("Asia/Shanghai");

    private static final Pattern MONTH_PERIOD =
            Pattern.compile("^(\\d{4})-(0[1-9]|1[0-2])$");
    private static final Pattern QUARTER_PERIOD =
            Pattern.compile("^(\\d{4})Q([1-4])$");

    private final FundLedgerQueryMapper mapper;

    @Override
    public FundLedgerSnapshotData composeMaintenanceFundData(Long tenantId, String period) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId must not be null");
        }
        Instant[] window = resolvePeriodWindow(period);
        return new FundLedgerSnapshotData(
                mapper.selectAccountBalances(tenantId),
                mapper.selectEntrySummariesInWindow(tenantId, window[0], window[1]));
    }

    /**
     * 把 period 字符串解析为 [startInclusive, endExclusive) 的 UTC Instant 对。
     *
     * <ul>
     *   <li>{@code "YYYY-MM"} → {@code [YYYY-MM-01 00:00 +08, 次月-01 00:00 +08)}</li>
     *   <li>{@code "YYYYQ[1-4]"} → {@code [季首月-01 00:00 +08, 次季首月-01 00:00 +08)}</li>
     * </ul>
     *
     * @throws IllegalArgumentException 格式不合法
     */
    static Instant[] resolvePeriodWindow(String period) {
        if (period == null || period.isBlank()) {
            throw new IllegalArgumentException("period must not be blank");
        }
        Matcher m = MONTH_PERIOD.matcher(period);
        if (m.matches()) {
            YearMonth ym = YearMonth.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
            return monthlyWindow(ym);
        }
        Matcher q = QUARTER_PERIOD.matcher(period);
        if (q.matches()) {
            int year = Integer.parseInt(q.group(1));
            int quarter = Integer.parseInt(q.group(2));
            int startMonth = (quarter - 1) * 3 + 1;
            return quarterlyWindow(Year.of(year), startMonth);
        }
        throw new IllegalArgumentException("Invalid period format: " + period
                + " (expect YYYY-MM or YYYYQ[1-4])");
    }

    private static Instant[] monthlyWindow(YearMonth ym) {
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.plusMonths(1).atDay(1);
        return new Instant[]{
                start.atStartOfDay(ZONE_SH).toInstant(),
                end.atStartOfDay(ZONE_SH).toInstant()
        };
    }

    private static Instant[] quarterlyWindow(Year year, int startMonth) {
        YearMonth startYm = YearMonth.of(year.getValue(), startMonth);
        YearMonth endYm = startYm.plusMonths(3);
        return new Instant[]{
                startYm.atDay(1).atStartOfDay(ZONE_SH).toInstant(),
                endYm.atDay(1).atStartOfDay(ZONE_SH).toInstant()
        };
    }
}
