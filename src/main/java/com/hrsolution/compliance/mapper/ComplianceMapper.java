package com.hrsolution.compliance.mapper;

import com.hrsolution.compliance.dto.ComplianceDtos;
import com.hrsolution.compliance.entity.Licence;
import com.hrsolution.compliance.entity.StatutoryChallan;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface ComplianceMapper {

    @Mapping(target = "clientId", expression = "java(licence.getClient() == null ? null : licence.getClient().getId())")
    @Mapping(target = "clientName", expression = "java(licence.getClient() == null ? null : (licence.getClient().getTradeName() != null ? licence.getClient().getTradeName() : licence.getClient().getLegalName()))")
    @Mapping(target = "daysUntilExpiry", expression = "java(licence.daysUntilExpiry())")
    @Mapping(target = "expiringSoon", expression = "java(licence.isExpiringSoon(60))")
    ComplianceDtos.LicenceResponse toLicenceResponse(Licence licence);

    ComplianceDtos.ChallanResponse toChallanResponse(StatutoryChallan challan);
}
