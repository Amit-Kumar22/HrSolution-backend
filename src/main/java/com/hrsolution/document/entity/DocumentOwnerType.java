package com.hrsolution.document.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * What a stored document belongs to.
 *
 * <p>{@link #publicAsset} is the important flag: it decides whether the file can
 * be served without authentication. Marketing assets are public by nature; a
 * worker's Aadhaar scan never is. Keeping that decision on the enum rather than
 * on each upload call site means a new owner type has to state its visibility
 * once, and cannot accidentally default to public.
 *
 * <p>{@link #storageCategory} is the directory segment used by the storage
 * layout, and is a server constant for exactly that reason.
 */
@Getter
@RequiredArgsConstructor
public enum DocumentOwnerType {

    /** The company logo. Singleton, so {@code ownerId} is null. */
    COMPANY_LOGO("logos", true),

    /** A testimonial's company logo or the photo of the person quoted. */
    TESTIMONIAL("testimonials", true),

    /** Hero image on a services page. */
    SERVICE_IMAGE("service-images", true),

    // ---- Private from here down. None of these may be served anonymously. ----

    /** Worker KYC and verification documents: Aadhaar, PAN, police check. */
    WORKER("worker-documents", false),

    /** Client-side paperwork such as a purchase order or GST certificate. */
    CLIENT("client-documents", false),

    /** A signed client contract. */
    CONTRACT("contracts", false),

    /** A company licence or registration certificate. */
    LICENCE("licences", false),

    /** A candidate's CV. */
    CANDIDATE("resumes", false),

    /** A PF, ESI or PT challan receipt. */
    CHALLAN("challans", false);

    /** Directory segment in the storage layout. */
    private final String storageCategory;

    /** Whether files of this type may be downloaded without authentication. */
    private final boolean publicAsset;
}
