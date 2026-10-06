package com.hrsolution.common.web;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * The single pagination envelope used by every list endpoint in this API.
 *
 * <p>Spring's own {@code Page} is deliberately not returned from controllers:
 * its JSON shape is unstable across Spring Data versions and it leaks
 * {@code Pageable}/{@code Sort} internals into the public contract.
 *
 * <p>Usage from a service, mapping entities to DTOs in one step:
 * <pre>{@code
 * Page<Worker> page = workerRepository.findAll(spec, pageable);
 * return PageResponse.from(page, workerMapper::toResponse);
 * }</pre>
 *
 * @param content       the rows on this page
 * @param page          zero-based page index
 * @param size          requested page size
 * @param totalElements total matching rows across all pages
 * @param totalPages    total number of pages
 * @param first         true when this is the first page
 * @param last          true when this is the last page
 * @param empty         true when this page has no rows
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        boolean empty) {

    public static <T> PageResponse<T> from(Page<T> source) {
        return new PageResponse<>(
                source.getContent(),
                source.getNumber(),
                source.getSize(),
                source.getTotalElements(),
                source.getTotalPages(),
                source.isFirst(),
                source.isLast(),
                source.isEmpty());
    }

    /** Maps each element with {@code converter} while preserving page metadata. */
    public static <E, D> PageResponse<D> from(Page<E> source, Function<E, D> converter) {
        return new PageResponse<>(
                source.getContent().stream().map(converter).toList(),
                source.getNumber(),
                source.getSize(),
                source.getTotalElements(),
                source.getTotalPages(),
                source.isFirst(),
                source.isLast(),
                source.isEmpty());
    }
}
