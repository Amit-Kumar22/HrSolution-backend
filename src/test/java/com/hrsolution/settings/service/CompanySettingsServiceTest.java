package com.hrsolution.settings.service;

import com.hrsolution.settings.dto.CompanySettingsResponse;
import com.hrsolution.settings.dto.UpdateCompanySettingsRequest;
import com.hrsolution.settings.entity.CompanySettings;
import com.hrsolution.settings.mapper.CompanySettingsMapper;
import com.hrsolution.settings.mapper.CompanySettingsMapperImpl;
import com.hrsolution.settings.repository.CompanySettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Service behaviour with a mocked repository.
 *
 * <p>The MapStruct-generated mapper is used for real rather than mocked - a
 * mocked mapper would assert nothing about whether fields actually copy across,
 * which is the main thing that can silently break when a field is added.
 */
@ExtendWith(MockitoExtension.class)
class CompanySettingsServiceTest {

    @Mock
    private CompanySettingsRepository companySettingsRepository;

    /**
     * Phase 3 added logo upload to this service. Mocked rather than exercised:
     * these tests cover the profile read/write logic, and the upload path has
     * its own coverage in LocalStorageServiceTest and FileTypeDetectorTest.
     */
    @Mock
    private com.hrsolution.document.service.DocumentService documentService;

    private final CompanySettingsMapper companySettingsMapper = new CompanySettingsMapperImpl();

    private CompanySettingsService companySettingsService;

    @BeforeEach
    void setUp() {
        companySettingsService = new CompanySettingsService(
                companySettingsRepository, companySettingsMapper, documentService);
    }

    @Test
    @DisplayName("get reads the singleton row by its fixed id and maps it")
    void getReadsSingletonRow() {
        CompanySettings stored = existingSettings();
        when(companySettingsRepository.findById(CompanySettingsService.SINGLETON_ID))
                .thenReturn(Optional.of(stored));

        CompanySettingsResponse response = companySettingsService.get();

        assertThat(response.legalName()).isEqualTo("Existing Company Private Limited");
        assertThat(response.stateCode()).isEqualTo("27");
        assertThat(response.gstin()).isEqualTo("27AABCS1234A1Z5");
        // Proves the lookup is pinned to id 1 and not a findAll().get(0).
        verify(companySettingsRepository).findById(1L);
    }

    @Test
    @DisplayName("get fails loudly when the seeded row is missing")
    void getFailsWhenRowMissing() {
        when(companySettingsRepository.findById(CompanySettingsService.SINGLETON_ID))
                .thenReturn(Optional.empty());

        // A 500 is correct here: the caller did nothing wrong, the database is
        // in a state the schema contract says is impossible. The message has to
        // point at the migration, because that is where the fix is.
        assertThatThrownBy(() -> companySettingsService.get())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("company_settings row id=1 is missing")
                .hasMessageContaining("V1");
    }

    @Test
    @DisplayName("update copies every editable field onto the stored row")
    void updateCopiesEditableFields() {
        CompanySettings stored = existingSettings();
        when(companySettingsRepository.findById(CompanySettingsService.SINGLETON_ID))
                .thenReturn(Optional.of(stored));
        when(companySettingsRepository.saveAndFlush(any(CompanySettings.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        companySettingsService.update(updateRequest());

        ArgumentCaptor<CompanySettings> captor = ArgumentCaptor.forClass(CompanySettings.class);
        verify(companySettingsRepository).saveAndFlush(captor.capture());
        CompanySettings saved = captor.getValue();

        assertThat(saved.getLegalName()).isEqualTo("Updated Company Private Limited");
        assertThat(saved.getTradeName()).isEqualTo("Updated Co");
        assertThat(saved.getCity()).isEqualTo("Nagpur");
        assertThat(saved.getStateCode()).isEqualTo("29");
        assertThat(saved.getGstin()).isEqualTo("29AABCS1234A1Z5");
        assertThat(saved.getPan()).isEqualTo("AABCS1234A");
        assertThat(saved.getBankIfsc()).isEqualTo("HDFC0001234");
        assertThat(saved.getPfEstablishmentCode()).isEqualTo("MHBAN1234567");
        assertThat(saved.getInstagramUrl()).isEqualTo("https://instagram.com/updated");
    }

    @Test
    @DisplayName("update clears a field that was sent empty")
    void updateClearsOmittedField() {
        CompanySettings stored = existingSettings();
        stored.setTagline("A tagline that should be cleared");
        when(companySettingsRepository.findById(CompanySettingsService.SINGLETON_ID))
                .thenReturn(Optional.of(stored));
        when(companySettingsRepository.saveAndFlush(any(CompanySettings.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // PUT has full-replacement semantics, so a null in the request must
        // actually blank the column rather than being skipped.
        companySettingsService.update(requestWithNullTagline());

        assertThat(stored.getTagline()).isNull();
    }

    @Test
    @DisplayName("update leaves the logo and the identity and audit columns alone")
    void updateDoesNotTouchLogoOrAuditColumns() {
        CompanySettings stored = existingSettings();
        stored.setLogoPath("logos/original-logo.png");
        when(companySettingsRepository.findById(CompanySettingsService.SINGLETON_ID))
                .thenReturn(Optional.of(stored));
        when(companySettingsRepository.saveAndFlush(any(CompanySettings.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        companySettingsService.update(updateRequest());

        // The logo is owned by its own upload endpoint. If it were mapped here,
        // saving the contact details form would wipe the company logo.
        assertThat(stored.getLogoPath()).isEqualTo("logos/original-logo.png");
        assertThat(stored.getId()).isEqualTo(CompanySettingsService.SINGLETON_ID);
        assertThat(stored.getVersion()).isEqualTo(3L);
        assertThat(stored.getCreatedBy()).isEqualTo("flyway");
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private CompanySettings existingSettings() {
        CompanySettings settings = new CompanySettings();
        settings.setId(CompanySettingsService.SINGLETON_ID);
        settings.setLegalName("Existing Company Private Limited");
        settings.setTradeName("Existing Co");
        settings.setCity("Pune");
        settings.setState("Maharashtra");
        settings.setStateCode("27");
        settings.setGstin("27AABCS1234A1Z5");
        settings.setVersion(3L);
        settings.setCreatedBy("flyway");
        return settings;
    }

    private UpdateCompanySettingsRequest updateRequest() {
        return new UpdateCompanySettingsRequest(
                "Updated Company Private Limited",   // legalName
                "Updated Co",                        // tradeName
                "Reliable staffing",                 // tagline
                "About the updated company.",        // about
                "Plot 42, Industrial Area",          // addressLine1
                "Near the bus depot",                // addressLine2
                "Nagpur",                            // city
                "Karnataka",                         // state
                "29",                                // stateCode
                "560001",                            // pincode
                "India",                             // country
                "+91 20 1234 5678",                  // phone
                "+91 20 1234 5679",                  // alternatePhone
                "info@updated.example.com",          // email
                "support@updated.example.com",       // supportEmail
                "https://updated.example.com",       // website
                "29AABCS1234A1Z5",                   // gstin
                "AABCS1234A",                        // pan
                "PNEA12345B",                        // tan
                "U74999MH2020PTC123456",             // cin
                "MHBAN1234567",                      // pfEstablishmentCode
                "31000123450000999",                 // esiEstablishmentCode
                "27999999999",                       // ptRegistrationNumber
                "HDFC Bank",                         // bankName
                "Shivajinagar",                      // bankBranch
                "50100123456789",                    // bankAccountNumber
                "HDFC0001234",                       // bankIfsc
                "https://linkedin.com/company/x",    // linkedinUrl
                "https://facebook.com/updated",      // facebookUrl
                "https://x.com/updated",             // twitterUrl
                "https://instagram.com/updated");    // instagramUrl
    }

    private UpdateCompanySettingsRequest requestWithNullTagline() {
        UpdateCompanySettingsRequest base = updateRequest();
        return new UpdateCompanySettingsRequest(
                base.legalName(), base.tradeName(), null, base.about(),
                base.addressLine1(), base.addressLine2(), base.city(), base.state(),
                base.stateCode(), base.pincode(), base.country(),
                base.phone(), base.alternatePhone(), base.email(), base.supportEmail(),
                base.website(), base.gstin(), base.pan(), base.tan(), base.cin(),
                base.pfEstablishmentCode(), base.esiEstablishmentCode(),
                base.ptRegistrationNumber(), base.bankName(), base.bankBranch(),
                base.bankAccountNumber(), base.bankIfsc(), base.linkedinUrl(),
                base.facebookUrl(), base.twitterUrl(), base.instagramUrl());
    }
}
