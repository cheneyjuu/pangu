package com.pangu.bootstrap.persistence;

import com.pangu.domain.model.voting.Denominator;
import com.pangu.domain.model.voting.SubjectStatus;
import com.pangu.domain.model.voting.SubjectType;
import com.pangu.domain.model.voting.VotingScope;
import com.pangu.domain.model.voting.VotingSubject;
import com.pangu.infrastructure.voting.DefaultVotingDenominatorResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link DefaultVotingDenominatorResolver} 真实 PG 集成测试。
 *
 * <p>验证 PRD §5.2「分母由空间节点反推」的双重去重契约：
 * <ul>
 *   <li>面积按 room_id 去重（共有产权一户多人不重复计面积）；</li>
 *   <li>人头按 primary_owner_uid 去重（一户多房不重复计人头）；</li>
 *   <li>account_status != 1（欠费挂起 / 冻结）的房产从分母中剔除；</li>
 *   <li>BUILDING scope 限定 scopeReferenceId，跨楼栋不可计入；</li>
 *   <li>aggregate_hash 为 64-hex SHA256 Merkle root；多次解析同一议题幂等。</li>
 * </ul>
 *
 * <p>测试隔离原则：使用 {@code TEST_TENANT_ID=99002}，自带 setUp/tearDown，
 * 不依赖 V1.1 mock 数据（避免被 mock 数据污染）。
 */
@SpringBootTest
public class DefaultVotingDenominatorResolverTest {

    private static final long TEST_TENANT_ID = 99002L;
    private static final long BUILDING_A = 990001L;
    private static final long BUILDING_B = 990002L;

    @Autowired
    private DefaultVotingDenominatorResolver resolver;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    public void setUp() {
        cleanUp();
    }

    @AfterEach
    public void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        // 子表清理：snapshot items → snapshot → owner_property → user
        jdbcTemplate.update(
                "DELETE FROM t_voting_denominator_item_snapshot "
                        + "WHERE snapshot_id IN (SELECT snapshot_id FROM t_voting_denominator_snapshot "
                        + "                       WHERE subject_id IN (SELECT subject_id FROM t_voting_subject WHERE tenant_id = ?))",
                TEST_TENANT_ID);
        jdbcTemplate.update(
                "DELETE FROM t_voting_denominator_snapshot "
                        + "WHERE subject_id IN (SELECT subject_id FROM t_voting_subject WHERE tenant_id = ?)",
                TEST_TENANT_ID);
        jdbcTemplate.update("DELETE FROM t_voting_subject WHERE tenant_id = ?", TEST_TENANT_ID);
        jdbcTemplate.update("DELETE FROM c_owner_property WHERE tenant_id = ?", TEST_TENANT_ID);
        // 测试用户 phone 形如 88xxxxxxxxx（11 位），生产/mock 数据 phone 通常不在该段位
        jdbcTemplate.update("DELETE FROM c_user WHERE phone LIKE '880%' AND length(phone) = 11");
    }

    /** 创建一个 c_user，返回回填的 uid。 */
    private Long insertUser(String phone) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO c_user(phone, auth_level) VALUES(?, 1) RETURNING uid",
                Long.class, phone);
    }

    /**
     * 插入一行 c_owner_property。
     *
     * @param votingDelegate 1=该 uid 是该 room 的投票代表
     * @param accountStatus  1=正常, 2=欠费挂起
     */
    private void insertOwnership(Long uid, Long buildingId, Long roomId, BigDecimal area,
                                 int votingDelegate, int accountStatus) {
        jdbcTemplate.update(
                "INSERT INTO c_owner_property(uid, tenant_id, building_id, room_id, build_area, is_voting_delegate, account_status) "
                        + "VALUES(?, ?, ?, ?, ?, ?, ?)",
                uid, TEST_TENANT_ID, buildingId, roomId, area, votingDelegate, accountStatus);
    }

    private VotingSubject communitySubject(Long subjectId) {
        return VotingSubject.builder()
                .subjectId(subjectId).tenantId(TEST_TENANT_ID).title("test")
                .subjectType(SubjectType.GENERAL).status(SubjectStatus.PUBLISHED)
                .scope(VotingScope.COMMUNITY)
                .build();
    }

    private VotingSubject buildingSubject(Long subjectId, Long buildingId) {
        return VotingSubject.builder()
                .subjectId(subjectId).tenantId(TEST_TENANT_ID).title("test")
                .subjectType(SubjectType.GENERAL).status(SubjectStatus.PUBLISHED)
                .scope(VotingScope.BUILDING)
                .scopeReferenceId(buildingId)
                .build();
    }

    /** 创建一个测试议题并返回 subject_id（主键回填）。 */
    private Long insertSubject(int subjectType, int scope, Long scopeReferenceId) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO t_voting_subject(tenant_id, title, subject_type, scope, scope_reference_id, status, party_ratio_floor) "
                        + "VALUES(?, ?, ?, ?, ?, 2, 0.50) RETURNING subject_id",
                Long.class,
                TEST_TENANT_ID, "denominator-test", subjectType, scope, scopeReferenceId);
    }

    @Test
    public void oneOwnerMultipleRooms_areaSummed_ownerCountedOnce() {
        // 一户多房：uid=A 拥有 3 个不同 room_id，每个 80/90/70
        Long uidA = insertUser("88010000001");
        insertOwnership(uidA, BUILDING_A, 100001L, new BigDecimal("80.00"), 1, 1);
        insertOwnership(uidA, BUILDING_A, 100002L, new BigDecimal("90.00"), 1, 1);
        insertOwnership(uidA, BUILDING_A, 100003L, new BigDecimal("70.00"), 1, 1);

        Long subjectId = insertSubject(3, 1, null);
        Denominator d = resolver.resolve(communitySubject(subjectId));

        assertEquals(0, new BigDecimal("240.00").compareTo(d.totalArea()),
                "三套房总面积应累加 = 240");
        assertEquals(1L, d.totalOwnerCount(),
                "同一自然人 uid 跨多 room 仅计 1 次人头");
        assertNotNull(d.snapshotHash());
        assertEquals(64, d.snapshotHash().length());
        assertNotNull(d.snapshotId());
    }

    @Test
    public void jointOwnership_areaCountedOnce_primaryDelegateUsed() {
        // 同一 room=200001 由 2 个 uid 共有，其中 uidB 是 voting_delegate
        Long uidA = insertUser("88020000001");
        Long uidB = insertUser("88020000002");
        insertOwnership(uidA, BUILDING_A, 200001L, new BigDecimal("100.00"), 0, 1);  // 非代表
        insertOwnership(uidB, BUILDING_A, 200001L, new BigDecimal("100.00"), 1, 1);  // 代表

        Long subjectId = insertSubject(3, 1, null);
        Denominator d = resolver.resolve(communitySubject(subjectId));

        assertEquals(0, new BigDecimal("100.00").compareTo(d.totalArea()),
                "共有产权一房应只计 1 份面积");
        assertEquals(1L, d.totalOwnerCount(),
                "同一 room 选 voting_delegate 作为代表，仅计 1 个 primary_owner");

        // 验证 SQL 选了正确的代表（uidB 而非 uidA）
        Map<String, Object> item = jdbcTemplate.queryForMap(
                "SELECT primary_owner_uid, co_owner_uids FROM t_voting_denominator_item_snapshot "
                        + "WHERE snapshot_id = ?", d.snapshotId());
        assertEquals(uidB.longValue(), ((Number) item.get("primary_owner_uid")).longValue(),
                "应选 is_voting_delegate=1 的 uid 作为代表");
        // co_owner_uids 应包含两个 uid（按 uid 升序）
        String coOwners = (String) item.get("co_owner_uids");
        assertTrue(coOwners.contains(uidA.toString()) && coOwners.contains(uidB.toString()),
                "co_owner_uids 应记录全部共有人 uid");
    }

    @Test
    public void accountSuspendedByFee_excludedFromDenominator() {
        // uidA 房产 account_status=2（欠费挂起）应被剔除；uidC 房产正常应入库
        Long uidA = insertUser("88030000001");
        Long uidC = insertUser("88030000003");
        insertOwnership(uidA, BUILDING_A, 300001L, new BigDecimal("80.00"), 1, 2);  // 欠费
        insertOwnership(uidC, BUILDING_A, 300002L, new BigDecimal("60.00"), 1, 1);  // 正常

        Long subjectId = insertSubject(3, 1, null);
        Denominator d = resolver.resolve(communitySubject(subjectId));

        // 仅 uidC 60 ㎡ 入库
        assertEquals(0, new BigDecimal("60.00").compareTo(d.totalArea()),
                "欠费挂起的房产应被剔除，仅剩 60 ㎡");
        assertEquals(1L, d.totalOwnerCount(),
                "欠费挂起的 uid 不计入分母");
    }

    @Test
    public void buildingScope_onlyCountsSpecifiedBuilding() {
        // BUILDING_A: 2 房；BUILDING_B: 1 房；scope=BUILDING 限定 A → 仅计 A
        Long uidA = insertUser("88040000001");
        Long uidB = insertUser("88040000002");
        Long uidC = insertUser("88040000003");
        insertOwnership(uidA, BUILDING_A, 400001L, new BigDecimal("100.00"), 1, 1);
        insertOwnership(uidB, BUILDING_A, 400002L, new BigDecimal("90.00"), 1, 1);
        insertOwnership(uidC, BUILDING_B, 400003L, new BigDecimal("80.00"), 1, 1);  // 不该计入

        Long subjectId = insertSubject(3, 2, BUILDING_A);
        Denominator d = resolver.resolve(buildingSubject(subjectId, BUILDING_A));

        assertEquals(0, new BigDecimal("190.00").compareTo(d.totalArea()),
                "BUILDING scope 应只计目标楼栋 A 内的面积 = 100+90 = 190");
        assertEquals(2L, d.totalOwnerCount(), "应只计 A 楼内的 2 个 owner");
    }

    @Test
    public void emptyOwnerPool_throwsIllegalState() {
        // 不插入任何 c_owner_property → 解析必失败
        Long subjectId = insertSubject(3, 1, null);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> resolver.resolve(communitySubject(subjectId)));
        assertTrue(ex.getMessage().contains("未查询到可计入的房产数据"));
    }

    @Test
    public void unitScope_explicitlyRejected() {
        // UNIT 范围本期未实现，应显式抛 IllegalStateException
        Long uidA = insertUser("88050000001");
        insertOwnership(uidA, BUILDING_A, 500001L, new BigDecimal("100.00"), 1, 1);

        Long subjectId = insertSubject(3, 1, null);
        VotingSubject subject = VotingSubject.builder()
                .subjectId(subjectId).tenantId(TEST_TENANT_ID).title("t")
                .subjectType(SubjectType.GENERAL).status(SubjectStatus.PUBLISHED)
                .scope(VotingScope.UNIT)
                .scopeReferenceId(123L)
                .build();

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> resolver.resolve(subject));
        assertTrue(ex.getMessage().contains("UNIT 范围分母解析未实现"));
    }

    @Test
    public void resolveSameSubjectTwice_isIdempotent_sameMerkleRoot() {
        // 同一议题反复 resolve（如重新结算）应得到相同 Merkle root + 相同 snapshot_id
        Long uidA = insertUser("88060000001");
        Long uidB = insertUser("88060000002");
        insertOwnership(uidA, BUILDING_A, 600001L, new BigDecimal("100.00"), 1, 1);
        insertOwnership(uidB, BUILDING_A, 600002L, new BigDecimal("90.00"), 1, 1);

        Long subjectId = insertSubject(3, 1, null);
        Denominator first = resolver.resolve(communitySubject(subjectId));
        Denominator second = resolver.resolve(communitySubject(subjectId));

        assertEquals(first.snapshotHash(), second.snapshotHash(),
                "同一数据 + 同一议题应得到相同 Merkle root");
        assertEquals(first.snapshotId(), second.snapshotId(),
                "议题维度 upsert 应返回同一 snapshot_id");
        assertEquals(0, first.totalArea().compareTo(second.totalArea()));
        assertEquals(first.totalOwnerCount(), second.totalOwnerCount());
    }
}
