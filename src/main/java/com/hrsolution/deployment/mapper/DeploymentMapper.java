package com.hrsolution.deployment.mapper;

import com.hrsolution.deployment.dto.DeploymentDtos;
import com.hrsolution.deployment.entity.Deployment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Entity to DTO mapping for deployments.
 *
 * <p>{@link #toClientResponse} is a separate record rather than the staff shape
 * with fields ignored, for the reason given in {@code DeploymentDtos}: a client
 * must not see the per-person wage or the margin, and making that a mapping
 * choice would put the guarantee one annotation away from gone.
 *
 * <p>No plain helper methods, per docs/decisions.md §24.
 */
@Mapper(imports = {java.time.LocalDate.class})
public interface DeploymentMapper {

    @Mapping(target = "workerId", expression = "java(entity.getWorker().getId())")
    @Mapping(target = "employeeCode", expression = "java(entity.getWorker().employeeCode())")
    @Mapping(target = "workerName", expression = "java(entity.getWorker().fullName())")
    @Mapping(target = "clientId", expression = "java(entity.getClient().getId())")
    @Mapping(target = "clientName", expression = "java(entity.getClient().displayName())")
    @Mapping(target = "siteId", expression = "java(entity.getSite().getId())")
    @Mapping(target = "siteCode", expression = "java(entity.getSite().getSiteCode())")
    @Mapping(target = "siteName", expression = "java(entity.getSite().getSiteName())")
    @Mapping(target = "requisitionId",
            expression = "java(entity.getRequisition() == null ? null "
                    + ": entity.getRequisition().getId())")
    @Mapping(target = "requisitionNumber",
            expression = "java(entity.getRequisition() == null ? null "
                    + ": entity.getRequisition().requisitionNumber())")
    @Mapping(target = "categoryId", expression = "java(entity.getCategory().getId())")
    @Mapping(target = "categoryName", expression = "java(entity.getCategory().getName())")
    @Mapping(target = "grossMarginPerMonth", expression = "java(entity.grossMarginPerMonth())")
    @Mapping(target = "billingBelowWage", expression = "java(entity.isBillingBelowWage())")
    @Mapping(target = "inForceToday", expression = "java(entity.isInForceOn(LocalDate.now()))")
    @Mapping(target = "daysServed", expression = "java(entity.daysServedOn(LocalDate.now()))")
    @Mapping(target = "displayLabel", expression = "java(entity.displayLabel())")
    DeploymentDtos.Response toResponse(Deployment entity);

    @Mapping(target = "workerId", expression = "java(entity.getWorker().getId())")
    @Mapping(target = "employeeCode", expression = "java(entity.getWorker().employeeCode())")
    @Mapping(target = "workerName", expression = "java(entity.getWorker().fullName())")
    @Mapping(target = "clientName", expression = "java(entity.getClient().displayName())")
    @Mapping(target = "siteCode", expression = "java(entity.getSite().getSiteCode())")
    @Mapping(target = "categoryName", expression = "java(entity.getCategory().getName())")
    @Mapping(target = "inForceToday", expression = "java(entity.isInForceOn(LocalDate.now()))")
    DeploymentDtos.SummaryResponse toSummaryResponse(Deployment entity);

    /** The client's view: no wage, no billing rate, no margin. */
    @Mapping(target = "employeeCode", expression = "java(entity.getWorker().employeeCode())")
    @Mapping(target = "workerName", expression = "java(entity.getWorker().fullName())")
    @Mapping(target = "siteCode", expression = "java(entity.getSite().getSiteCode())")
    @Mapping(target = "siteName", expression = "java(entity.getSite().getSiteName())")
    @Mapping(target = "categoryName", expression = "java(entity.getCategory().getName())")
    @Mapping(target = "inForceToday", expression = "java(entity.isInForceOn(LocalDate.now()))")
    @Mapping(target = "policeVerified",
            expression = "java(entity.getWorker().getPoliceVerificationStatus()"
                    + ".permitsSecurityDeployment())")
    DeploymentDtos.ClientResponse toClientResponse(Deployment entity);
}
