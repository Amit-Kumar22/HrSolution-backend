package com.hrsolution.common;

import com.hrsolution.content.dto.ContentDtos;
import com.hrsolution.content.entity.ServiceOffering;
import com.hrsolution.content.entity.Testimonial;
import com.hrsolution.content.mapper.ContentMapperImpl;
import com.hrsolution.settings.dto.PublicCompanyProfileResponse;
import com.hrsolution.settings.entity.CompanySettings;
import com.hrsolution.settings.mapper.CompanySettingsMapperImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression guard against a MapStruct trap that bit this project once and
 * would otherwise bite it again.
 *
 * <p><strong>What happened.</strong> {@code CompanySettingsMapper} and
 * {@code ContentMapper} each had a convenience default method:
 *
 * <pre>{@code
 * default String publicUrl(String storageKey) { ... }
 * }</pre>
 *
 * <p>MapStruct treats any non-private method on a mapper as a candidate
 * <em>conversion</em> for its signature. A {@code String -> String} method is
 * therefore applicable to every single String property, and MapStruct silently
 * applied it to all of them - 87 fields in one generated mapper and 49 in the
 * other. The company's legal name came back as
 * {@code "/api/v1/public/files/Shree Manpower Services Private Limited"}.
 *
 * <p>Nothing warns about this. It compiles, the application starts, and every
 * string in those responses is quietly corrupted.
 *
 * <p>The fix was to call the helper statically through
 * {@code @Mapper(imports = PublicFileUrls.class)} instead. These tests assert
 * the outcome rather than the mechanism, so they will catch the same mistake
 * however it is reintroduced.
 */
class MapperHelperLeakTest {

    private static final String URL_PREFIX = "/api/v1/public/files/";

    private final CompanySettingsMapperImpl settingsMapper = new CompanySettingsMapperImpl();
    private final ContentMapperImpl contentMapper = new ContentMapperImpl();

    @Test
    @DisplayName("only logoUrl is URL-prefixed on the public company profile")
    void companyProfileStringsAreNotPrefixed() {
        CompanySettings settings = new CompanySettings();
        settings.setLegalName("Shree Manpower Services Private Limited");
        settings.setTradeName("Shree Manpower");
        settings.setCity("Pune");
        settings.setGstin("27AABCS1234A1Z5");
        settings.setPan("AABCS1234A");
        settings.setEmail("info@example.com");
        settings.setPhone("+91 20 1234 5678");
        settings.setLogoPath("logos/2026/10/abc.png");

        PublicCompanyProfileResponse response = settingsMapper.toPublicProfile(settings);

        assertThat(response.legalName()).isEqualTo("Shree Manpower Services Private Limited");
        assertThat(response.tradeName()).isEqualTo("Shree Manpower");
        assertThat(response.city()).isEqualTo("Pune");
        assertThat(response.gstin()).isEqualTo("27AABCS1234A1Z5");
        assertThat(response.pan()).isEqualTo("AABCS1234A");
        assertThat(response.email()).isEqualTo("info@example.com");
        assertThat(response.phone()).isEqualTo("+91 20 1234 5678");

        // The one field that genuinely IS a file URL.
        assertThat(response.logoUrl()).isEqualTo(URL_PREFIX + "logos/2026/10/abc.png");
    }

    @Test
    @DisplayName("only heroImageUrl is URL-prefixed on a public service page")
    void serviceStringsAreNotPrefixed() {
        ServiceOffering service = new ServiceOffering();
        service.setSlug("manpower-supply");
        service.setTitle("Manpower Supply");
        service.setSummary("Contract workforce for factories and warehouses.");
        service.setDescription("Full page body.");
        service.setIcon("users");
        service.setHeroImagePath("service-images/2026/10/hero.jpg");

        ContentDtos.PublicServiceResponse response = contentMapper.toPublicResponse(service);

        assertThat(response.slug()).isEqualTo("manpower-supply");
        assertThat(response.title()).isEqualTo("Manpower Supply");
        assertThat(response.summary()).isEqualTo("Contract workforce for factories and warehouses.");
        assertThat(response.description()).isEqualTo("Full page body.");
        assertThat(response.icon()).isEqualTo("users");

        // Falls back to title/summary when no meta override is set.
        assertThat(response.metaTitle()).isEqualTo("Manpower Supply");
        assertThat(response.metaDescription())
                .isEqualTo("Contract workforce for factories and warehouses.");

        assertThat(response.heroImageUrl()).isEqualTo(URL_PREFIX + "service-images/2026/10/hero.jpg");
    }

    @Test
    @DisplayName("only logoUrl and photoUrl are URL-prefixed on a public testimonial")
    void testimonialStringsAreNotPrefixed() {
        Testimonial testimonial = new Testimonial();
        testimonial.setClientName("Rajesh Kulkarni");
        testimonial.setClientCompany("Bharat Textiles Private Limited");
        testimonial.setDesignation("Plant Head");
        testimonial.setContent("They handled our compliance end to end.");
        testimonial.setRating(5);
        testimonial.setLogoPath("testimonials/2026/10/logo.png");
        testimonial.setPhotoPath("testimonials/2026/10/photo.jpg");

        ContentDtos.PublicTestimonialResponse response = contentMapper.toPublicResponse(testimonial);

        assertThat(response.clientName()).isEqualTo("Rajesh Kulkarni");
        assertThat(response.clientCompany()).isEqualTo("Bharat Textiles Private Limited");
        assertThat(response.designation()).isEqualTo("Plant Head");
        assertThat(response.content()).isEqualTo("They handled our compliance end to end.");
        assertThat(response.attribution())
                .isEqualTo("Rajesh Kulkarni, Plant Head, Bharat Textiles Private Limited");

        assertThat(response.logoUrl()).isEqualTo(URL_PREFIX + "testimonials/2026/10/logo.png");
        assertThat(response.photoUrl()).isEqualTo(URL_PREFIX + "testimonials/2026/10/photo.jpg");
    }

    @Test
    @DisplayName("an absent image maps to a null URL, not to a dangling prefix")
    void absentImagesStayAbsent() {
        CompanySettings settings = new CompanySettings();
        settings.setLegalName("No Logo Private Limited");

        PublicCompanyProfileResponse response = settingsMapper.toPublicProfile(settings);

        // A bare "/api/v1/public/files/" would render as a broken image.
        assertThat(response.logoUrl()).isNull();
        assertThat(response.legalName()).isEqualTo("No Logo Private Limited");
    }
}
