package com.hrsolution.requisition.mapper;

import com.hrsolution.requisition.dto.RequisitionDtos;
import com.hrsolution.requisition.entity.Requisition;
import com.hrsolution.requisition.entity.RequisitionStatusHistory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * Entity to DTO mapping for requisitions.
 *
 * <p>The three fulfilment numbers - deployed, outstanding, urgent - all depend
 * on the deployments table, so {@code RequisitionService} supplies them after
 * mapping rather than this interface guessing at them.
 *
 * <p>No plain helper methods, per docs/decisions.md §24.
 */
@Mapper(imports = {java.time.LocalDate.class})
public interface RequisitionMapper {

    @Mapping(target = "requisitionNumber", expression = "java(entity.requisitionNumber())")
    @Mapping(target = "clientId", expression = "java(entity.getClient().getId())")
    @Mapping(target = "clientName", expression = "java(entity.getClient().displayName())")
    @Mapping(target = "siteId", expression = "java(entity.getSite().getId())")
    @Mapping(target = "siteCode", expression = "java(entity.getSite().getSiteCode())")
    @Mapping(target = "siteName", expression = "java(entity.getSite().getSiteName())")
    @Mapping(target = "siteStateCode", expression = "java(entity.getSite().getStateCode())")
    @Mapping(target = "categoryId", expression = "java(entity.getCategory().getId())")
    @Mapping(target = "categoryName", expression = "java(entity.getCategory().getName())")
    @Mapping(target = "effectiveSkillLevel", expression = "java(entity.effectiveSkillLevel())")
    @Mapping(target = "allowedNextStatuses",
            expression = "java(java.util.List.copyOf(entity.getStatus().allowedNextStatuses()))")
    @Mapping(target = "terminal", expression = "java(entity.getStatus().isTerminal())")
    @Mapping(target = "acceptsDeployments",
            expression = "java(entity.getStatus().acceptsDeployments())")
    @Mapping(target = "raisedByUserId",
            expression = "java(entity.getRaisedBy() == null ? null "
                    + ": entity.getRaisedBy().getId())")
    @Mapping(target = "raisedByName",
            expression = "java(entity.getRaisedBy() == null ? null "
                    + ": entity.getRaisedBy().fullName())")
    @Mapping(target = "approvedByUserId",
            expression = "java(entity.getApprovedBy() == null ? null "
                    + ": entity.getApprovedBy().getId())")
    @Mapping(target = "approvedByName",
            expression = "java(entity.getApprovedBy() == null ? null "
                    + ": entity.getApprovedBy().fullName())")
    // Need the deployments table.
    @Mapping(target = "quantityDeployed", ignore = true)
    @Mapping(target = "quantityOutstanding", ignore = true)
    @Mapping(target = "urgent", ignore = true)
    RequisitionDtos.Response toResponse(Requisition entity);

    @Mapping(target = "requisitionNumber", expression = "java(entity.requisitionNumber())")
    @Mapping(target = "clientName", expression = "java(entity.getClient().displayName())")
    @Mapping(target = "siteCode", expression = "java(entity.getSite().getSiteCode())")
    @Mapping(target = "categoryName", expression = "java(entity.getCategory().getName())")
    @Mapping(target = "quantityDeployed", ignore = true)
    @Mapping(target = "quantityOutstanding", ignore = true)
    @Mapping(target = "urgent", ignore = true)
    RequisitionDtos.SummaryResponse toSummaryResponse(Requisition entity);

    /** The client's own view: no approver, no internal notes. */
    @Mapping(target = "requisitionNumber", expression = "java(entity.requisitionNumber())")
    @Mapping(target = "siteId", expression = "java(entity.getSite().getId())")
    @Mapping(target = "siteCode", expression = "java(entity.getSite().getSiteCode())")
    @Mapping(target = "siteName", expression = "java(entity.getSite().getSiteName())")
    @Mapping(target = "categoryName", expression = "java(entity.getCategory().getName())")
    @Mapping(target = "skillLevel", expression = "java(entity.effectiveSkillLevel())")
    @Mapping(target = "statusLabel", expression = "java(entity.getStatus().clientFacingLabel())")
    @Mapping(target = "terminal", expression = "java(entity.getStatus().isTerminal())")
    @Mapping(target = "editable", expression = "java(entity.getStatus().isEditableByClient())")
    @Mapping(target = "quantityDeployed", ignore = true)
    @Mapping(target = "quantityOutstanding", ignore = true)
    RequisitionDtos.ClientResponse toClientResponse(Requisition entity);

    RequisitionDtos.StatusHistoryEntry toHistoryEntry(RequisitionStatusHistory entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    // Resolved from ids by the service, which checks they relate to each other.
    @Mapping(target = "client", ignore = true)
    @Mapping(target = "site", ignore = true)
    @Mapping(target = "category", ignore = true)
    // Lifecycle and approval have their own endpoints and audit events.
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "statusHistory", ignore = true)
    @Mapping(target = "raisedBy", ignore = true)
    @Mapping(target = "approvedBy", ignore = true)
    @Mapping(target = "approvedAt", ignore = true)
    @Mapping(target = "rejectionReason", ignore = true)
    // Primitive with a default: a null in the request must not zero it.
    @Mapping(target = "quantityRequired", ignore = true)
    void applyRequest(RequisitionDtos.Request request, @MappingTarget Requisition entity);
}
