package com.hrsolution.content.entity;

import com.hrsolution.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A client quote shown on the marketing site.
 *
 * <p>{@link #published} defaults to false even though only staff can create a
 * testimonial. Quoting a named person at a named client company is a
 * reputational commitment, so someone has to approve it deliberately rather
 * than it going live the moment it is typed.
 */
@Entity
@Table(name = "testimonials")
@Getter
@Setter
public class Testimonial extends BaseEntity {

    /** The person quoted. */
    @Column(name = "client_name", nullable = false, length = 120)
    private String clientName;

    @Column(name = "client_company", length = 200)
    private String clientCompany;

    @Column(name = "designation", length = 120)
    private String designation;

    @Column(name = "content", nullable = false, length = 1500)
    private String content;

    /** 1–5, optional. A quote without a star rating is still worth showing. */
    @Column(name = "rating")
    private Integer rating;

    /** Storage key for the client company's logo. */
    @Column(name = "logo_path", length = 400)
    private String logoPath;

    /** Storage key for a photo of the person quoted. */
    @Column(name = "photo_path", length = 400)
    private String photoPath;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(name = "published", nullable = false)
    private boolean published = false;

    /** "Rajesh Kulkarni, Plant Head, Bharat Textiles" — whichever parts exist. */
    public String attribution() {
        StringBuilder attribution = new StringBuilder(clientName);
        if (designation != null && !designation.isBlank()) {
            attribution.append(", ").append(designation);
        }
        if (clientCompany != null && !clientCompany.isBlank()) {
            attribution.append(", ").append(clientCompany);
        }
        return attribution.toString();
    }
}
