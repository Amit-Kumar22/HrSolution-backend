package com.hrsolution.content.entity;

import com.hrsolution.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One service the company sells — a page on the marketing site.
 *
 * <p>Content lives in the database rather than in code or template files so
 * that marketing copy can be reworded without a deployment. That is the whole
 * reason this table exists.
 *
 * <p>{@link #published} defaults to false, so a half-written page cannot appear
 * on the public site by accident.
 */
@Entity
@Table(name = "service_offerings")
@Getter
@Setter
public class ServiceOffering extends BaseEntity {

    /**
     * URL segment, e.g. {@code manpower-supply} for
     * {@code /services/manpower-supply}.
     *
     * <p>Treat as immutable once published. Changing it breaks every inbound
     * link and discards whatever search ranking the page had earned; a rename
     * should be a new row plus a redirect, not an edit.
     */
    @Column(name = "slug", nullable = false, length = 120)
    private String slug;

    @Column(name = "title", nullable = false, length = 160)
    private String title;

    /** Card text on the services index. */
    @Column(name = "summary", nullable = false, length = 400)
    private String summary;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** Icon name for the site to render; this API does not host icons. */
    @Column(name = "icon", length = 60)
    private String icon;

    @Column(name = "hero_image_path", length = 400)
    private String heroImagePath;

    /**
     * Search-result title, when it should differ from {@link #title}.
     *
     * <p>Separate because the two have different jobs: a page heading reads
     * naturally in context, while a search snippet has to carry the keywords and
     * fit roughly 60 characters. Falls back to {@link #title} when blank.
     */
    @Column(name = "meta_title", length = 160)
    private String metaTitle;

    /** Search-result snippet; falls back to {@link #summary} when blank. */
    @Column(name = "meta_description", length = 320)
    private String metaDescription;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(name = "published", nullable = false)
    private boolean published = false;

    /** The title to use for SEO, with the fallback applied. */
    public String effectiveMetaTitle() {
        return metaTitle == null || metaTitle.isBlank() ? title : metaTitle;
    }

    public String effectiveMetaDescription() {
        return metaDescription == null || metaDescription.isBlank() ? summary : metaDescription;
    }
}
