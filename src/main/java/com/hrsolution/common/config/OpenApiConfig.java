package com.hrsolution.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI / OpenAPI document configuration.
 *
 * <p>Served at {@code /swagger-ui.html}, with the raw document at
 * {@code /v3/api-docs}. Both are switched off in the prod profile.
 *
 * <p>The {@code bearerAuth} scheme is declared now so that Phase 2 only has to
 * reference it. It is intentionally <em>not</em> added as a global security
 * requirement yet: in Phase 1 no endpoint requires a token, and advertising one
 * would make Swagger show a padlock on endpoints that do not need it.
 */
@Configuration
@RequiredArgsConstructor
public class OpenApiConfig {

    public static final String BEARER_SCHEME = "bearerAuth";

    private final AppProperties appProperties;

    @Bean
    public OpenAPI hrSolutionOpenApi() {
        AppProperties.OpenApi settings = appProperties.getOpenapi();

        Info info = new Info()
                .title(settings.getTitle())
                .version(settings.getVersion())
                .description(settings.getDescription())
                .license(new License().name("Proprietary"));

        if (!settings.getContactName().isBlank() || !settings.getContactEmail().isBlank()) {
            info.contact(new Contact()
                    .name(settings.getContactName())
                    .email(settings.getContactEmail()));
        }

        return new OpenAPI()
                .info(info)
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .name(BEARER_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste the access token returned by "
                                        + "POST /api/v1/auth/login. Valid for 15 minutes.")));
    }
}
