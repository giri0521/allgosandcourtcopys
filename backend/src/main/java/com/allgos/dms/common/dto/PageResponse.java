package com.allgos.dms.common.dto;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * The wire shape of every paged list.
 *
 * <p>Spring's own {@code Page} serialises its internal structure, which is not a stable contract and
 * warns as much on start-up. Declaring our own record means the web app sees the same four fields
 * from every list endpoint, whatever the entity.
 */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
