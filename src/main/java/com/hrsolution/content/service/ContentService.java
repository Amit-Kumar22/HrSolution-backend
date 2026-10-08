package com.hrsolution.content.service;

import com.hrsolution.common.config.CachingConfig;
import com.hrsolution.common.error.BusinessRuleException;
import com.hrsolution.common.error.DuplicateResourceException;
import com.hrsolution.common.error.ResourceNotFoundException;
import com.hrsolution.common.storage.FileTypeDetector;
import com.hrsolution.content.dto.ContentDtos;
import com.hrsolution.content.entity.Industry;
import com.hrsolution.content.entity.ServiceOffering;
import com.hrsolution.content.entity.Testimonial;
import com.hrsolution.content.mapper.ContentMapper;
import com.hrsolution.content.repository.IndustryRepository;
import com.hrsolution.content.repository.ServiceOfferingRepository;
import com.hrsolution.content.repository.TestimonialRepository;
import com.hrsolution.document.entity.DocumentOwnerType;
import com.hrsolution.document.entity.StoredDocument;
import com.hrsolution.document.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * The editable marketing content: services, industries and testimonials.
 *
 * <p>One service for all three because they share identical mechanics -
 * publish flags, display ordering, slug uniqueness and cache eviction - and
 * three near-identical classes would be three places to fix the same bug.
 *
 * <p>Public reads are cached and every write evicts the whole
 * {@code publicContent} cache. Evicting everything rather than one entry is
 * deliberate: the lists and the detail views overlap, the data is tiny, and
 * content edits are rare, so precision here would buy nothing and risk serving
 * a stale services index after an edit.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentService {

    private final ServiceOfferingRepository serviceRepository;
    private final IndustryRepository industryRepository;
    private final TestimonialRepository testimonialRepository;
    private final ContentMapper contentMapper;
    private final DocumentService documentService;

    // ==================================================================
    // Public reads
    // ==================================================================

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CachingConfig.PUBLIC_CONTENT, key = "'services'")
    public List<ContentDtos.PublicServiceResponse> publishedServices() {
        return serviceRepository.findByPublishedTrueOrderByDisplayOrderAscTitleAsc().stream()
                .map(contentMapper::toPublicResponse)
                .toList();
    }

    /**
     * One published service by slug.
     *
     * <p>An unpublished draft is reported as not found rather than returned, so
     * a work-in-progress page cannot be read by guessing its URL.
     */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CachingConfig.PUBLIC_CONTENT, key = "'service:' + #slug")
    public ContentDtos.PublicServiceResponse publishedServiceBySlug(String slug) {
        return serviceRepository.findBySlugAndPublishedTrue(slug)
                .map(contentMapper::toPublicResponse)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No published service exists at '%s'.".formatted(slug)));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CachingConfig.PUBLIC_CONTENT, key = "'industries'")
    public List<ContentDtos.PublicIndustryResponse> publishedIndustries() {
        return industryRepository.findByPublishedTrueOrderByDisplayOrderAscNameAsc().stream()
                .map(contentMapper::toPublicResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CachingConfig.PUBLIC_CONTENT, key = "'testimonials'")
    public List<ContentDtos.PublicTestimonialResponse> publishedTestimonials() {
        return testimonialRepository.findByPublishedTrueOrderByDisplayOrderAscCreatedAtDesc().stream()
                .map(contentMapper::toPublicResponse)
                .toList();
    }

    // ==================================================================
    // Services - admin
    // ==================================================================

    @Transactional(readOnly = true)
    public List<ContentDtos.ServiceResponse> listServices() {
        return serviceRepository.findAllByOrderByDisplayOrderAscTitleAsc().stream()
                .map(contentMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ContentDtos.ServiceResponse getService(Long id) {
        return contentMapper.toResponse(loadService(id));
    }

    @Transactional
    @CacheEvict(cacheNames = CachingConfig.PUBLIC_CONTENT, allEntries = true)
    public ContentDtos.ServiceResponse createService(ContentDtos.ServiceRequest request) {
        String slug = request.slug().trim().toLowerCase();
        if (serviceRepository.existsBySlug(slug)) {
            throw DuplicateResourceException.of("A service", "slug", slug);
        }

        ServiceOffering service = new ServiceOffering();
        contentMapper.applyRequest(request, service);
        service.setSlug(slug);
        service.setPublished(Boolean.TRUE.equals(request.published()));

        ServiceOffering saved = serviceRepository.save(service);
        log.info("Created service '{}' (published={})", saved.getSlug(), saved.isPublished());
        return contentMapper.toResponse(saved);
    }

    @Transactional
    @CacheEvict(cacheNames = CachingConfig.PUBLIC_CONTENT, allEntries = true)
    public ContentDtos.ServiceResponse updateService(Long id, ContentDtos.ServiceRequest request) {
        ServiceOffering service = loadService(id);
        String newSlug = request.slug().trim().toLowerCase();

        // Changing a published page's slug breaks every inbound link and
        // discards whatever search ranking it had. Blocked while published; the
        // correct move is to unpublish, rename, and add a redirect at the edge.
        if (!service.getSlug().equals(newSlug)) {
            if (service.isPublished()) {
                throw new BusinessRuleException(
                        ("The slug of a published page cannot be changed - it would break inbound "
                                + "links and lose its search ranking. Unpublish '%s' first if you "
                                + "are sure.").formatted(service.getSlug()));
            }
            if (serviceRepository.existsBySlug(newSlug)) {
                throw DuplicateResourceException.of("A service", "slug", newSlug);
            }
            service.setSlug(newSlug);
        }

        contentMapper.applyRequest(request, service);
        service.setSlug(newSlug);
        if (request.published() != null) {
            service.setPublished(request.published());
        }
        return contentMapper.toResponse(service);
    }

    @Transactional
    @CacheEvict(cacheNames = CachingConfig.PUBLIC_CONTENT, allEntries = true)
    public ContentDtos.ServiceResponse setServicePublished(Long id, boolean published) {
        ServiceOffering service = loadService(id);
        service.setPublished(published);
        log.info("Service '{}' {}", service.getSlug(), published ? "published" : "unpublished");
        return contentMapper.toResponse(service);
    }

    @Transactional
    @CacheEvict(cacheNames = CachingConfig.PUBLIC_CONTENT, allEntries = true)
    public ContentDtos.ServiceResponse uploadServiceHeroImage(Long id, MultipartFile file) {
        ServiceOffering service = loadService(id);
        StoredDocument document = documentService.upload(
                file, DocumentOwnerType.SERVICE_IMAGE, id, FileTypeDetector.IMAGES);
        service.setHeroImagePath(document.getStorageKey());
        return contentMapper.toResponse(service);
    }

    /** Hard delete, unlike most entities: page content is not a business record. */
    @Transactional
    @CacheEvict(cacheNames = CachingConfig.PUBLIC_CONTENT, allEntries = true)
    public void deleteService(Long id) {
        ServiceOffering service = loadService(id);
        if (service.isPublished()) {
            throw new BusinessRuleException(
                    "Unpublish this service before deleting it, so the page cannot vanish "
                            + "from under live traffic by accident.");
        }
        serviceRepository.delete(service);
        log.info("Deleted service '{}'", service.getSlug());
    }

    // ==================================================================
    // Industries - admin
    // ==================================================================

    @Transactional(readOnly = true)
    public List<ContentDtos.IndustryResponse> listIndustries() {
        return industryRepository.findAllByOrderByDisplayOrderAscNameAsc().stream()
                .map(contentMapper::toResponse)
                .toList();
    }

    @Transactional
    @CacheEvict(cacheNames = CachingConfig.PUBLIC_CONTENT, allEntries = true)
    public ContentDtos.IndustryResponse createIndustry(ContentDtos.IndustryRequest request) {
        String slug = request.slug().trim().toLowerCase();
        if (industryRepository.existsBySlug(slug)) {
            throw DuplicateResourceException.of("An industry", "slug", slug);
        }

        Industry industry = new Industry();
        contentMapper.applyRequest(request, industry);
        industry.setSlug(slug);
        industry.setPublished(Boolean.TRUE.equals(request.published()));

        return contentMapper.toResponse(industryRepository.save(industry));
    }

    @Transactional
    @CacheEvict(cacheNames = CachingConfig.PUBLIC_CONTENT, allEntries = true)
    public ContentDtos.IndustryResponse updateIndustry(Long id, ContentDtos.IndustryRequest request) {
        Industry industry = loadIndustry(id);
        String newSlug = request.slug().trim().toLowerCase();

        if (!industry.getSlug().equals(newSlug) && industryRepository.existsBySlug(newSlug)) {
            throw DuplicateResourceException.of("An industry", "slug", newSlug);
        }

        contentMapper.applyRequest(request, industry);
        industry.setSlug(newSlug);
        if (request.published() != null) {
            industry.setPublished(request.published());
        }
        return contentMapper.toResponse(industry);
    }

    @Transactional
    @CacheEvict(cacheNames = CachingConfig.PUBLIC_CONTENT, allEntries = true)
    public void deleteIndustry(Long id) {
        industryRepository.delete(loadIndustry(id));
    }

    // ==================================================================
    // Testimonials - admin
    // ==================================================================

    @Transactional(readOnly = true)
    public Page<ContentDtos.TestimonialResponse> listTestimonials(Pageable pageable) {
        return testimonialRepository.findAllByOrderByDisplayOrderAscCreatedAtDesc(pageable)
                .map(contentMapper::toResponse);
    }

    @Transactional
    @CacheEvict(cacheNames = CachingConfig.PUBLIC_CONTENT, allEntries = true)
    public ContentDtos.TestimonialResponse createTestimonial(ContentDtos.TestimonialRequest request) {
        Testimonial testimonial = new Testimonial();
        contentMapper.applyRequest(request, testimonial);
        // Unpublished unless explicitly asked for: naming a real person at a
        // real client is a reputational commitment that wants a second look.
        testimonial.setPublished(Boolean.TRUE.equals(request.published()));
        return contentMapper.toResponse(testimonialRepository.save(testimonial));
    }

    @Transactional
    @CacheEvict(cacheNames = CachingConfig.PUBLIC_CONTENT, allEntries = true)
    public ContentDtos.TestimonialResponse updateTestimonial(
            Long id, ContentDtos.TestimonialRequest request) {
        Testimonial testimonial = loadTestimonial(id);
        contentMapper.applyRequest(request, testimonial);
        if (request.published() != null) {
            testimonial.setPublished(request.published());
        }
        return contentMapper.toResponse(testimonial);
    }

    @Transactional
    @CacheEvict(cacheNames = CachingConfig.PUBLIC_CONTENT, allEntries = true)
    public ContentDtos.TestimonialResponse setTestimonialPublished(Long id, boolean published) {
        Testimonial testimonial = loadTestimonial(id);
        testimonial.setPublished(published);
        return contentMapper.toResponse(testimonial);
    }

    @Transactional
    @CacheEvict(cacheNames = CachingConfig.PUBLIC_CONTENT, allEntries = true)
    public ContentDtos.TestimonialResponse uploadTestimonialImage(
            Long id, MultipartFile file, boolean isLogo) {
        Testimonial testimonial = loadTestimonial(id);
        StoredDocument document = documentService.upload(
                file, DocumentOwnerType.TESTIMONIAL, id, FileTypeDetector.IMAGES);

        if (isLogo) {
            testimonial.setLogoPath(document.getStorageKey());
        } else {
            testimonial.setPhotoPath(document.getStorageKey());
        }
        return contentMapper.toResponse(testimonial);
    }

    @Transactional
    @CacheEvict(cacheNames = CachingConfig.PUBLIC_CONTENT, allEntries = true)
    public void deleteTestimonial(Long id) {
        testimonialRepository.delete(loadTestimonial(id));
    }

    // ==================================================================

    private ServiceOffering loadService(Long id) {
        return serviceRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Service", id));
    }

    private Industry loadIndustry(Long id) {
        return industryRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Industry", id));
    }

    private Testimonial loadTestimonial(Long id) {
        return testimonialRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Testimonial", id));
    }
}
