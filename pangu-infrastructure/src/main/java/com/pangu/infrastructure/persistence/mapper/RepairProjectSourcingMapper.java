// 关联业务：读写维修工程项目方案级邀价、报价版本和中选供应商快照。
package com.pangu.infrastructure.persistence.mapper;

import com.pangu.infrastructure.persistence.entity.RepairProjectSourcingRows.InvitationRow;
import com.pangu.infrastructure.persistence.entity.RepairProjectSourcingRows.QuoteLineRow;
import com.pangu.infrastructure.persistence.entity.RepairProjectSourcingRows.QuoteRow;
import com.pangu.infrastructure.persistence.entity.RepairProjectSourcingRows.SelectionRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RepairProjectSourcingMapper {

    int insertInvitation(InvitationRow row);

    List<InvitationRow> listInvitations(
            @Param("projectId") Long projectId,
            @Param("planId") Long planId,
            @Param("tenantId") Long tenantId);

    List<InvitationRow> listSupplierInvitations(
            @Param("supplierDeptId") Long supplierDeptId);

    InvitationRow findSupplierInvitation(
            @Param("invitationId") Long invitationId,
            @Param("projectId") Long projectId,
            @Param("supplierDeptId") Long supplierDeptId);

    InvitationRow findInvitation(
            @Param("invitationId") Long invitationId,
            @Param("projectId") Long projectId,
            @Param("planId") Long planId,
            @Param("tenantId") Long tenantId,
            @Param("supplierDeptId") Long supplierDeptId);

    boolean supplierInvited(
            @Param("projectId") Long projectId,
            @Param("planId") Long planId,
            @Param("tenantId") Long tenantId,
            @Param("supplierDeptId") Long supplierDeptId);

    int nextInvitationRound(
            @Param("projectId") Long projectId,
            @Param("planId") Long planId,
            @Param("tenantId") Long tenantId,
            @Param("supplierDeptId") Long supplierDeptId);

    int markInvitationSubmitted(@Param("invitationId") Long invitationId, @Param("tenantId") Long tenantId);

    int insertQuote(QuoteRow row);

    int insertQuoteLine(QuoteLineRow row);

    QuoteRow findQuote(
            @Param("quoteId") Long quoteId,
            @Param("projectId") Long projectId,
            @Param("planId") Long planId,
            @Param("tenantId") Long tenantId);

    QuoteRow findLatestSupplierQuote(
            @Param("projectId") Long projectId,
            @Param("planId") Long planId,
            @Param("tenantId") Long tenantId,
            @Param("supplierDeptId") Long supplierDeptId);

    List<QuoteRow> listQuotes(
            @Param("projectId") Long projectId,
            @Param("planId") Long planId,
            @Param("tenantId") Long tenantId);

    List<QuoteLineRow> listQuoteLines(@Param("quoteIds") List<Long> quoteIds);

    int supersedeQuote(@Param("quoteId") Long quoteId, @Param("supersededByQuoteId") Long supersededByQuoteId);

    int requestQuoteRevision(@Param("quoteId") Long quoteId);

    int countInitialInvitedSuppliers(
            @Param("projectId") Long projectId,
            @Param("planId") Long planId,
            @Param("tenantId") Long tenantId);

    int countActiveConfirmedQuotes(
            @Param("projectId") Long projectId,
            @Param("planId") Long planId,
            @Param("tenantId") Long tenantId);

    int insertSelection(SelectionRow row);

    SelectionRow findCurrentSelection(
            @Param("projectId") Long projectId,
            @Param("planId") Long planId,
            @Param("tenantId") Long tenantId);
}
