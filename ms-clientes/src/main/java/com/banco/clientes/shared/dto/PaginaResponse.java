package com.banco.clientes.shared.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Contrato estable de paginación: serializar {@link Page} directamente produce un JSON que
 * Spring no garantiza entre versiones.
 */
public record PaginaResponse<T>(
        List<T> contenido,
        int pagina,
        int tamano,
        long totalElementos,
        int totalPaginas) {

    public static <T> PaginaResponse<T> de(Page<T> page) {
        return new PaginaResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
