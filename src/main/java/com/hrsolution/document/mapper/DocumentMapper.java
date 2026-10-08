package com.hrsolution.document.mapper;

import com.hrsolution.common.web.ApiPaths;
import com.hrsolution.common.web.PublicFileUrls;
import com.hrsolution.document.dto.DocumentResponse;
import com.hrsolution.document.entity.StoredDocument;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface DocumentMapper {

    @Mapping(target = "image", expression = "java(entity.isImage())")
    @Mapping(target = "downloadUrl", expression = "java(downloadUrl(entity))")
    DocumentResponse toResponse(StoredDocument entity);

    /**
     * Public assets get their public URL; private ones get the authenticated
     * download route by id.
     *
     * <p>A private document's storage key is deliberately not included
     * anywhere in the response - the id is all a permitted caller needs.
     *
     * <p>{@code @Named} so MapStruct will not auto-select this as a
     * {@code StoredDocument -> String} conversion for some other property; it is
     * only used where the expression above calls it by name.
     */
    @org.mapstruct.Named("downloadUrl")
    default String downloadUrl(StoredDocument entity) {
        if (entity.isPublicAsset()) {
            return PublicFileUrls.of(entity.getStorageKey());
        }
        return ApiPaths.V1 + "/documents/" + entity.getId() + "/download";
    }
}
