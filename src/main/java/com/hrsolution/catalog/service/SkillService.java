package com.hrsolution.catalog.service;

import com.hrsolution.catalog.dto.SkillDtos;
import com.hrsolution.catalog.entity.ManpowerCategory;
import com.hrsolution.catalog.entity.Skill;
import com.hrsolution.catalog.mapper.SkillMapper;
import com.hrsolution.catalog.repository.ManpowerCategoryRepository;
import com.hrsolution.catalog.repository.SkillRepository;
import com.hrsolution.common.config.CachingConfig;
import com.hrsolution.common.error.DuplicateResourceException;
import com.hrsolution.common.error.FieldValidationException;
import com.hrsolution.common.error.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The skills master, and which skills a category typically requires.
 *
 * <p>Category-to-skill mapping is <strong>advisory</strong>. Phase 6 records
 * what each worker actually holds, and the deployment screen surfaces the gap -
 * but nothing is blocked for a missing skill. Staffing decisions are made by
 * people who can see the gap and judge whether it matters, and a system that
 * refused a deployment because a certificate had not been scanned yet would
 * simply be worked around.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillService {

    private final SkillRepository skillRepository;
    private final ManpowerCategoryRepository categoryRepository;
    private final SkillMapper skillMapper;

    @Transactional(readOnly = true)
    public List<SkillDtos.Response> listActive() {
        return skillRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(skillMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SkillDtos.Response> listAll() {
        return skillRepository.findAllByOrderByNameAsc().stream()
                .map(skillMapper::toResponse)
                .toList();
    }

    @Transactional
    public SkillDtos.Response create(SkillDtos.Request request) {
        String name = request.name().trim();
        if (skillRepository.existsByNameIgnoreCase(name)) {
            throw DuplicateResourceException.of("A skill", "name", name);
        }

        Skill skill = new Skill();
        skillMapper.applyRequest(request, skill);
        skill.setName(name);
        skill.setActive(request.active() == null || request.active());

        return skillMapper.toResponse(skillRepository.save(skill));
    }

    @Transactional
    public SkillDtos.Response update(Long skillId, SkillDtos.Request request) {
        Skill skill = load(skillId);
        String name = request.name().trim();

        if (!skill.getName().equalsIgnoreCase(name)
                && skillRepository.existsByNameIgnoreCase(name)) {
            throw DuplicateResourceException.of("A skill", "name", name);
        }

        skillMapper.applyRequest(request, skill);
        skill.setName(name);
        if (request.active() != null) {
            skill.setActive(request.active());
        }
        return skillMapper.toResponse(skill);
    }

    /**
     * Deactivates a skill. There is no delete: workers from Phase 6 reference
     * skills, and removing one would erase a recorded certification.
     */
    @Transactional
    public SkillDtos.Response setActive(Long skillId, boolean active) {
        Skill skill = load(skillId);
        skill.setActive(active);
        return skillMapper.toResponse(skill);
    }

    // ==================================================================
    // Category mapping
    // ==================================================================

    @Transactional(readOnly = true)
    public List<SkillDtos.Response> listForCategory(Long categoryId) {
        ManpowerCategory category = categoryRepository.findWithSkillsById(categoryId)
                .orElseThrow(() -> ResourceNotFoundException.of("Manpower category", categoryId));

        return category.getSkills().stream()
                .sorted(java.util.Comparator.comparing(Skill::getName))
                .map(skillMapper::toResponse)
                .toList();
    }

    @Transactional
    @CacheEvict(cacheNames = CachingConfig.MANPOWER_CATEGORIES, allEntries = true)
    public List<SkillDtos.Response> assignToCategory(Long categoryId,
                                                     SkillDtos.AssignToCategoryRequest request) {
        ManpowerCategory category = categoryRepository.findWithSkillsById(categoryId)
                .orElseThrow(() -> ResourceNotFoundException.of("Manpower category", categoryId));

        Set<Long> requestedIds = request.skillIds() == null ? Set.of() : request.skillIds();
        Set<Skill> skills = requestedIds.isEmpty()
                ? new HashSet<>()
                : skillRepository.findByIdIn(requestedIds);

        // Report the unknown ids rather than silently dropping them - a typo
        // that quietly assigns nothing is harder to notice than an error.
        if (skills.size() != requestedIds.size()) {
            Set<Long> found = new HashSet<>();
            skills.forEach(skill -> found.add(skill.getId()));
            Set<Long> missing = new HashSet<>(requestedIds);
            missing.removeAll(found);
            throw FieldValidationException.of("skillIds", "unknown skill id(s): " + missing);
        }

        category.replaceSkills(skills);
        log.info("Category {} now requires {} skill(s)", category.getCode(), skills.size());

        return skills.stream()
                .sorted(java.util.Comparator.comparing(Skill::getName))
                .map(skillMapper::toResponse)
                .toList();
    }

    private Skill load(Long skillId) {
        return skillRepository.findById(skillId)
                .orElseThrow(() -> ResourceNotFoundException.of("Skill", skillId));
    }
}
