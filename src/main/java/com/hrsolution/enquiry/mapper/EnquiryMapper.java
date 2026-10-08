package com.hrsolution.enquiry.mapper;

import com.hrsolution.enquiry.EnquiryReference;
import com.hrsolution.enquiry.dto.ContactMessageDtos;
import com.hrsolution.enquiry.dto.EnquiryDtos;
import com.hrsolution.enquiry.entity.ContactMessage;
import com.hrsolution.enquiry.entity.Enquiry;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Entity to DTO mapping for enquiries and contact messages.
 *
 * <p>{@code imports} makes {@link EnquiryReference} resolvable inside the
 * generated class - a MapStruct {@code expression} is pasted verbatim and does
 * not inherit this file's imports.
 *
 * <p>Helpers are deliberately static and external rather than default methods
 * here. MapStruct treats any non-private method on a mapper as a candidate
 * conversion for its signature, so a {@code Long -> String} default method would
 * be applied to every unrelated {@code Long -> String} property.
 */
@Mapper(imports = EnquiryReference.class)
public interface EnquiryMapper {

    @Mapping(target = "reference", expression = "java(EnquiryReference.of(entity.getId()))")
    @Mapping(target = "categoryId", expression = "java(entity.getCategory() == null ? null : entity.getCategory().getId())")
    @Mapping(target = "categoryLabel", expression = "java(entity.categoryLabel())")
    @Mapping(target = "assignedToUserId", expression = "java(entity.getAssignedTo() == null ? null : entity.getAssignedTo().getId())")
    @Mapping(target = "assignedToName", expression = "java(entity.getAssignedTo() == null ? null : entity.getAssignedTo().fullName())")
    EnquiryDtos.Response toResponse(Enquiry entity);

    ContactMessageDtos.Response toResponse(ContactMessage entity);
}
