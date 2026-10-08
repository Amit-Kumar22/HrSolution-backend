package com.hrsolution.content.entity;

import com.hrsolution.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A sector the company supplies staff to — the Industries Served page.
 *
 * <p>Deliberately simpler than {@link ServiceOffering}: an industry is a short
 * card with a name and a sentence, not a page of its own, so it carries no
 * long-form body or SEO overrides.
 */
@Entity
@Table(name = "industries")
@Getter
@Setter
public class Industry extends BaseEntity {

    @Column(name = "slug", nullable = false, length = 120)
    private String slug;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", length = 600)
    private String description;

    @Column(name = "icon", length = 60)
    private String icon;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(name = "published", nullable = false)
    private boolean published = false;
}
