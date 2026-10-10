package com.hrsolution.client.mapper;

import com.hrsolution.client.dto.ClientDtos;
import com.hrsolution.client.dto.ClientContractDtos;
import com.hrsolution.client.dto.ClientSiteDtos;
import com.hrsolution.client.dto.RateCardDtos;
import com.hrsolution.client.entity.Client;
import com.hrsolution.client.entity.ClientContract;
import com.hrsolution.client.entity.ClientSite;
import com.hrsolution.client.entity.ClientUser;
import com.hrsolution.client.entity.RateCard;
import com.hrsolution.client.entity.ServiceChargeType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Entity to DTO mapping for the whole client module.
 *
 * <p>Note that there are <strong>no plain helper methods on this interface</strong>.
 * MapStruct treats any non-private method on a mapper as a candidate type
 * conversion for its signature, so a {@code String f(String)} helper would get
 * applied to every String property - which silently corrupted 136 fields
 * earlier in this project. The two helpers that must live here are
 * {@code @Named} so MapStruct only uses them where an expression asks by name.
 * See docs/decisions.md §24.
 *
 * <p>Fields needing a GST comparison or child counts are not mapped here at
 * all: they depend on data outside the entity, so the services pass them in.
 */
@Mapper
public interface ClientMapper {

    // ---------------- Client ----------------
    //
    // gstTreatment and the two counts come from outside the entity, so they are
    // set by the service after mapping rather than guessed at here.

    @Mapping(target = "clientCode", expression = "java(entity.clientCode())")
    @Mapping(target = "displayName", expression = "java(entity.displayName())")
    @Mapping(target = "industryId", expression = "java(entity.getIndustry() == null ? null : entity.getIndustry().getId())")
    @Mapping(target = "industryName", expression = "java(entity.getIndustry() == null ? null : entity.getIndustry().getName())")
    @Mapping(target = "gstTreatment", ignore = true)
    @Mapping(target = "activeSiteCount", ignore = true)
    @Mapping(target = "activeContractCount", ignore = true)
    ClientDtos.Response toResponse(Client entity);

    @Mapping(target = "clientCode", expression = "java(entity.clientCode())")
    @Mapping(target = "displayName", expression = "java(entity.displayName())")
    @Mapping(target = "gstTreatment", ignore = true)
    ClientDtos.SummaryResponse toSummaryResponse(Client entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    // Each has its own endpoint and its own audit event, so a profile edit
    // cannot silently change them.
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "industry", ignore = true)
    @Mapping(target = "onboardedOn", ignore = true)
    @Mapping(target = "paymentTermsDays", ignore = true)
    void applyRequest(ClientDtos.Request request, @MappingTarget Client entity);

    // ---------------- Client users ----------------

    @Mapping(target = "userId", expression = "java(entity.getUser().getId())")
    @Mapping(target = "email", expression = "java(entity.getUser().getEmail())")
    @Mapping(target = "fullName", expression = "java(entity.getUser().fullName())")
    @Mapping(target = "phone", expression = "java(entity.getUser().getPhone())")
    @Mapping(target = "userStatus", expression = "java(entity.getUser().getStatus().name())")
    ClientDtos.UserResponse toUserResponse(ClientUser entity);

    // ---------------- Sites ----------------

    @Mapping(target = "clientId", expression = "java(entity.getClient().getId())")
    @Mapping(target = "clientName", expression = "java(entity.getClient().displayName())")
    @Mapping(target = "displayLabel", expression = "java(entity.displayLabel())")
    ClientSiteDtos.Response toResponse(ClientSite entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "client", ignore = true)
    @Mapping(target = "active", ignore = true)
    void applyRequest(ClientSiteDtos.Request request, @MappingTarget ClientSite entity);

    // ---------------- Contracts ----------------
    //
    // documentCount comes from the documents table, so the service sets it.

    @Mapping(target = "clientId", expression = "java(entity.getClient().getId())")
    @Mapping(target = "clientName", expression = "java(entity.getClient().displayName())")
    @Mapping(target = "serviceChargeLabel",
            expression = "java(serviceChargeLabel(entity.getServiceChargeType(), entity.getServiceChargeValue()))")
    @Mapping(target = "effectivePaymentTermsDays",
            expression = "java(entity.effectivePaymentTermsDays())")
    @Mapping(target = "inForceToday",
            expression = "java(entity.isInForceOn(java.time.LocalDate.now()))")
    @Mapping(target = "daysUntilExpiry",
            expression = "java(entity.daysUntilExpiry(java.time.LocalDate.now()))")
    @Mapping(target = "documentCount", ignore = true)
    ClientContractDtos.Response toResponse(ClientContract entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "client", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "terminatedOn", ignore = true)
    @Mapping(target = "terminationReason", ignore = true)
    void applyRequest(ClientContractDtos.Request request, @MappingTarget ClientContract entity);

    // ---------------- Rate cards ----------------

    @Mapping(target = "clientId", expression = "java(entity.getClient().getId())")
    @Mapping(target = "clientName", expression = "java(entity.getClient().displayName())")
    @Mapping(target = "categoryId", expression = "java(entity.getCategory().getId())")
    @Mapping(target = "categoryName", expression = "java(entity.getCategory().getName())")
    @Mapping(target = "skillLevel", expression = "java(entity.getCategory().getSkillLevel())")
    @Mapping(target = "siteId", expression = "java(entity.getSite() == null ? null : entity.getSite().getId())")
    @Mapping(target = "siteName", expression = "java(entity.getSite() == null ? null : entity.getSite().getSiteName())")
    @Mapping(target = "siteSpecific", expression = "java(entity.isSiteSpecific())")
    @Mapping(target = "grossMarginPerWorker", expression = "java(entity.grossMarginPerWorker())")
    @Mapping(target = "billingBelowWage", expression = "java(entity.isBillingBelowWage())")
    @Mapping(target = "current", expression = "java(entity.isCurrent())")
    RateCardDtos.Response toResponse(RateCard entity);

    /**
     * "8.50% of wages" or "Rs 1,200.00 per worker per month".
     *
     * <p>{@code @Named} so MapStruct treats it as callable-by-name only. Left
     * un-annotated it would be a candidate conversion from
     * {@code ServiceChargeType} to {@code String} wherever one was needed.
     */
    @Named("serviceChargeLabel")
    default String serviceChargeLabel(ServiceChargeType type, BigDecimal value) {
        if (type == null || value == null) {
            return null;
        }
        return switch (type) {
            case PERCENTAGE -> "%s%% of wages".formatted(value.stripTrailingZeros().toPlainString());
            case FIXED_PER_WORKER ->
                    "Rs %s per worker per month".formatted(value.stripTrailingZeros().toPlainString());
        };
    }

    /** Today, as a named helper for expressions that need it. */
    @Named("today")
    default LocalDate today() {
        return LocalDate.now();
    }
}
