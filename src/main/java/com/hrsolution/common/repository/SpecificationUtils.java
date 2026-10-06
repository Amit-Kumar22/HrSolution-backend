package com.hrsolution.common.repository;

import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.Collection;

/**
 * Building blocks for the dynamic list filters used by every search endpoint.
 *
 * <p>Each helper returns {@link Specification#unrestricted()} when its argument
 * is null or blank, so an unsupplied filter contributes nothing. That means a
 * controller can chain every possible filter unconditionally and let the absent
 * ones fall away:
 *
 * <pre>{@code
 * Specification<Worker> spec = Specification.<Worker>unrestricted()
 *         .and(SpecificationUtils.notDeleted())
 *         .and(SpecificationUtils.equal("status", status))              // ignored when status == null
 *         .and(SpecificationUtils.equal("category.id", categoryId))
 *         .and(SpecificationUtils.anyContainsIgnoreCase(search, "firstName", "lastName", "employeeCode"))
 *         .and(SpecificationUtils.between("dateOfJoining", joinedFrom, joinedTo));
 *
 * return PageResponse.from(workerRepository.findAll(spec, pageable), workerMapper::toResponse);
 * }</pre>
 *
 * <p>Attribute names may be dotted to walk to-one associations
 * ({@code "category.name"}). Note that path navigation produces an inner join,
 * so a dotted filter on an optional association also filters out rows where the
 * association is null - use an explicit left join if that is not what you want.
 */
public final class SpecificationUtils {

    private SpecificationUtils() {
    }

    /** Excludes soft-deleted rows. Mandatory on any {@code SoftDeletableEntity} query. */
    public static <T> Specification<T> notDeleted() {
        return (root, query, builder) -> builder.isFalse(root.get("deleted"));
    }

    public static <T> Specification<T> equal(String attribute, Object value) {
        if (value == null) {
            return Specification.unrestricted();
        }
        return (root, query, builder) -> builder.equal(resolve(root, attribute), value);
    }

    public static <T> Specification<T> notEqual(String attribute, Object value) {
        if (value == null) {
            return Specification.unrestricted();
        }
        return (root, query, builder) -> builder.notEqual(resolve(root, attribute), value);
    }

    /** Case-insensitive {@code LIKE %value%} on a single text attribute. */
    public static <T> Specification<T> containsIgnoreCase(String attribute, String value) {
        if (!StringUtils.hasText(value)) {
            return Specification.unrestricted();
        }
        String pattern = "%" + escapeWildcards(value.trim().toLowerCase()) + "%";
        return (root, query, builder) ->
                builder.like(builder.lower(resolve(root, attribute).as(String.class)), pattern, '\\');
    }

    /**
     * Case-insensitive {@code LIKE %value%} across several attributes, OR-ed
     * together - the usual behaviour of a single free-text search box.
     */
    public static <T> Specification<T> anyContainsIgnoreCase(String value, String... attributes) {
        if (!StringUtils.hasText(value) || attributes == null || attributes.length == 0) {
            return Specification.unrestricted();
        }
        String pattern = "%" + escapeWildcards(value.trim().toLowerCase()) + "%";
        return (root, query, builder) -> {
            var predicates = new jakarta.persistence.criteria.Predicate[attributes.length];
            for (int i = 0; i < attributes.length; i++) {
                predicates[i] = builder.like(
                        builder.lower(resolve(root, attributes[i]).as(String.class)), pattern, '\\');
            }
            return builder.or(predicates);
        };
    }

    public static <T> Specification<T> in(String attribute, Collection<?> values) {
        if (values == null || values.isEmpty()) {
            return Specification.unrestricted();
        }
        return (root, query, builder) -> resolve(root, attribute).in(values);
    }

    public static <T, C extends Comparable<? super C>> Specification<T> greaterOrEqual(
            String attribute, C value) {
        if (value == null) {
            return Specification.unrestricted();
        }
        return (root, query, builder) -> builder.greaterThanOrEqualTo(comparable(root, attribute), value);
    }

    public static <T, C extends Comparable<? super C>> Specification<T> lessOrEqual(
            String attribute, C value) {
        if (value == null) {
            return Specification.unrestricted();
        }
        return (root, query, builder) -> builder.lessThanOrEqualTo(comparable(root, attribute), value);
    }

    /** Inclusive range. Either bound may be null, giving an open-ended range. */
    public static <T, C extends Comparable<? super C>> Specification<T> between(
            String attribute, C from, C to) {
        return SpecificationUtils.<T, C>greaterOrEqual(attribute, from)
                .and(SpecificationUtils.lessOrEqual(attribute, to));
    }

    public static <T> Specification<T> isTrue(String attribute) {
        return (root, query, builder) -> builder.isTrue(root.get(attribute));
    }

    public static <T> Specification<T> isFalse(String attribute) {
        return (root, query, builder) -> builder.isFalse(root.get(attribute));
    }

    public static <T> Specification<T> isNull(String attribute) {
        return (root, query, builder) -> builder.isNull(resolve(root, attribute));
    }

    public static <T> Specification<T> isNotNull(String attribute) {
        return (root, query, builder) -> builder.isNotNull(resolve(root, attribute));
    }

    /** Walks a dotted attribute path such as {@code "client.state"}. */
    private static Path<?> resolve(Root<?> root, String attribute) {
        if (!attribute.contains(".")) {
            return root.get(attribute);
        }
        Path<?> path = root;
        for (String segment : attribute.split("\\.")) {
            path = path.get(segment);
        }
        return path;
    }

    /**
     * The Criteria API needs a {@code Path<C extends Comparable>} for range
     * predicates, but a dotted path resolves to {@code Path<?>}. The cast is
     * safe as long as the attribute really is of the compared type - a mismatch
     * surfaces immediately as a query failure in the repository test.
     */
    @SuppressWarnings("unchecked")
    private static <C extends Comparable<? super C>> Path<C> comparable(Root<?> root, String attribute) {
        return (Path<C>) resolve(root, attribute);
    }

    /**
     * Prevents a user typing {@code %} or {@code _} in a search box from turning
     * it into a wildcard that matches every row.
     */
    private static String escapeWildcards(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
