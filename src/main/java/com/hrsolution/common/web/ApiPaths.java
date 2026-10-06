package com.hrsolution.common.web;

/**
 * Base path constants, so the API version lives in exactly one place.
 *
 * <p>Controllers should write {@code @RequestMapping(ApiPaths.V1 + "/clients")}
 * rather than hardcoding {@code "/api/v1/clients"}.
 */
public final class ApiPaths {

    /** Root of the versioned REST API. */
    public static final String V1 = "/api/v1";

    /** Endpoints under this prefix are reachable without authentication. */
    public static final String PUBLIC_V1 = V1 + "/public";

    private ApiPaths() {
    }
}
