package br.com.reservas.shared.web;

import java.util.List;
import org.springframework.data.domain.Page;

/** Formato de paginacao do contrato (docs/03): `{ content, page, size, totalElements, totalPages }`. */
public record PageDto<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <S, T> PageDto<T> of(Page<S> page, java.util.function.Function<S, T> mapper) {
        return new PageDto<>(page.getContent().stream().map(mapper).toList(), page.getNumber(), page.getSize(),
            page.getTotalElements(), page.getTotalPages());
    }
}
