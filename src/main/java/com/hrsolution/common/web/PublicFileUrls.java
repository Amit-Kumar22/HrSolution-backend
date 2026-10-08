package com.hrsolution.common.web;

/**
 * Builds the public URL for a stored asset.
 *
 * <p>In one place so the route is defined once. Storage keys of public assets
 * appear in marketing-site HTML, so the shape of this URL is effectively part
 * of the public contract.
 *
 * <p>Only ever called with the key of a document whose {@code publicAsset} flag
 * is true - the endpoint itself re-checks that flag, so a mistake here leaks a
 * broken link rather than a private file.
 */
public final class PublicFileUrls {

    /** Route served by {@code PublicFileController}. */
    public static final String PUBLIC_FILE_PATH = ApiPaths.PUBLIC_V1 + "/files/";

    private PublicFileUrls() {
    }

    /** Returns null for a null or blank key, so an absent image stays absent. */
    public static String of(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return null;
        }
        return PUBLIC_FILE_PATH + storageKey;
    }
}
