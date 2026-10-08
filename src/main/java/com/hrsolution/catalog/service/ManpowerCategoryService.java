package com.hrsolution.catalog.service;

import com.hrsolution.catalog.dto.ManpowerCategoryDtos;
import com.hrsolution.catalog.entity.ManpowerCategory;
import com.hrsolution.catalog.mapper.ManpowerCategoryMapper;
import com.hrsolution.catalog.repository.ManpowerCategoryRepository;
import com.hrsolution.common.config.CachingConfig;
import com.hrsolution.common.error.BusinessRuleException;
import com.hrsolution.common.error.DuplicateResourceException;
import com.hrsolution.common.error.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The manpower category master.
 *
 * <p>Read-heavy and almost never written: every enquiry form, requisition
 * screen and rate card references it, while the list changes a few times a
 * year. So reads are cached and writes evict.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ManpowerCategoryService {

    private final ManpowerCategoryRepository categoryRepository;
    private final ManpowerCategoryMapper categoryMapper;

    @Transactional(readOnly = true)
    @Cacheable(CachingConfig.MANPOWER_CATEGORIES)
    public List<ManpowerCategoryDtos.PublicResponse> listActiveForPublic() {
        return categoryRepository.findByActiveTrueOrderByDisplayOrderAscNameAsc().stream()
                .map(categoryMapper::toPublicResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ManpowerCategoryDtos.Response> listAll() {
        return categoryRepository.findAllByOrderByDisplayOrderAscNameAsc().stream()
                .map(categoryMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ManpowerCategoryDtos.Response get(Long id) {
        return categoryMapper.toResponse(load(id));
    }

    @Transactional
    @CacheEvict(cacheNames = CachingConfig.MANPOWER_CATEGORIES, allEntries = true)
    public ManpowerCategoryDtos.Response create(ManpowerCategoryDtos.Request request) {
        String code = request.code().trim().toUpperCase();

        if (categoryRepository.existsByCode(code)) {
            throw DuplicateResourceException.of("A manpower category", "code", code);
        }
        if (categoryRepository.existsByNameIgnoreCase(request.name().trim())) {
            throw DuplicateResourceException.of("A manpower category", "name", request.name());
        }

        ManpowerCategory category = new ManpowerCategory();
        categoryMapper.applyRequest(request, category);
        category.setCode(code);
        category.setActive(request.active() == null || request.active());

        ManpowerCategory saved = categoryRepository.save(category);
        log.info("Created manpower category {} ({})", saved.getCode(), saved.getSkillLevel());
        return categoryMapper.toResponse(saved);
    }

    @Transactional
    @CacheEvict(cacheNames = CachingConfig.MANPOWER_CATEGORIES, allEntries = true)
    public ManpowerCategoryDtos.Response update(Long id, ManpowerCategoryDtos.Request request) {
        ManpowerCategory category = load(id);
        String newCode = request.code().trim().toUpperCase();

        // The code is the stable identifier used by Excel imports and exports.
        // Changing it would silently break every file keyed on the old value,
        // so it is fixed once created - rename the display name instead.
        if (!category.getCode().equals(newCode)) {
            throw new BusinessRuleException(
                    "A category code cannot be changed once created (currently '%s'). "
                            .formatted(category.getCode())
                            + "Deactivate this category and create a new one if the code is wrong.");
        }

        SkillLevelChange change = new SkillLevelChange(category.getSkillLevel(), request.skillLevel());

        categoryMapper.applyRequest(request, category);
        if (request.active() != null) {
            category.setActive(request.active());
        }

        if (change.changed()) {
            // Not cosmetic: the skill level selects which state minimum wage
            // applies, so this changes the legal wage floor for every worker in
            // this category from here on.
            log.warn("Skill level of category {} changed from {} to {} - this changes the "
                            + "applicable minimum wage band",
                    category.getCode(), change.from(), change.to());
        }

        return categoryMapper.toResponse(category);
    }

    private record SkillLevelChange(com.hrsolution.catalog.entity.SkillLevel from,
                                    com.hrsolution.catalog.entity.SkillLevel to) {
        boolean changed() {
            return from != null && to != null && from != to;
        }
    }

    /**
     * Deactivates a category. There is no delete.
     *
     * <p>Enquiries, requisitions, deployments and payroll items all reference
     * categories; deleting one would orphan history. Deactivating removes it
     * from dropdowns while leaving every existing record readable.
     */
    @Transactional
    @CacheEvict(cacheNames = CachingConfig.MANPOWER_CATEGORIES, allEntries = true)
    public ManpowerCategoryDtos.Response deactivate(Long id) {
        ManpowerCategory category = load(id);
        if (!category.isActive()) {
            throw new BusinessRuleException("This category is already inactive.");
        }
        category.setActive(false);
        log.info("Deactivated manpower category {}", category.getCode());
        return categoryMapper.toResponse(category);
    }

    @Transactional
    @CacheEvict(cacheNames = CachingConfig.MANPOWER_CATEGORIES, allEntries = true)
    public ManpowerCategoryDtos.Response activate(Long id) {
        ManpowerCategory category = load(id);
        category.setActive(true);
        return categoryMapper.toResponse(category);
    }

    /** For other services that need the entity, such as enquiry submission. */
    @Transactional(readOnly = true)
    public ManpowerCategory load(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Manpower category", id));
    }
}
