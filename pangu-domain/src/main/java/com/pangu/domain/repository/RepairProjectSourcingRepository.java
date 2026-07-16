// 关联业务：持久化维修工程项目方案级邀价、报价版本和中选供应商快照。
package com.pangu.domain.repository;

import com.pangu.domain.model.repair.RepairProjectSourcing.Invitation;
import com.pangu.domain.model.repair.RepairProjectSourcing.Quote;
import com.pangu.domain.model.repair.RepairProjectSourcing.Selection;

import java.util.List;
import java.util.Optional;

public interface RepairProjectSourcingRepository {

    Invitation insertInvitation(Invitation invitation);

    List<Invitation> listInvitations(Long projectId, Long planId, Long tenantId);

    List<Invitation> listSupplierInvitations(Long tenantId, Long supplierDeptId);

    Optional<Invitation> findInvitation(
            Long invitationId, Long projectId, Long planId, Long tenantId, Long supplierDeptId);

    boolean supplierInvited(Long projectId, Long planId, Long tenantId, Long supplierDeptId);

    int nextInvitationRound(Long projectId, Long planId, Long tenantId, Long supplierDeptId);

    int markInvitationSubmitted(Long invitationId, Long tenantId);

    Quote insertQuote(Quote quote);

    Optional<Quote> findQuote(Long quoteId, Long projectId, Long planId, Long tenantId);

    Optional<Quote> findLatestSupplierQuote(
            Long projectId, Long planId, Long tenantId, Long supplierDeptId);

    List<Quote> listQuotes(Long projectId, Long planId, Long tenantId);

    int supersedeQuote(Long quoteId, Long supersededByQuoteId);

    int requestQuoteRevision(Long quoteId);

    int countInitialInvitedSuppliers(Long projectId, Long planId, Long tenantId);

    int countActiveConfirmedQuotes(Long projectId, Long planId, Long tenantId);

    Selection insertSelection(Selection selection);

    Optional<Selection> findCurrentSelection(Long projectId, Long planId, Long tenantId);
}
