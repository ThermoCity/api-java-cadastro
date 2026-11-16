package br.com.fiap.ThermoCity.common;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

@Schema(description = "Resposta paginada padrão")
public record PaginaResponse<T>(
        List<T> conteudo,
        @Schema(description = "Página atual (começa em 0)", example = "0") int pagina,
        @Schema(description = "Itens por página", example = "10") int tamanho,
        @Schema(description = "Total de itens em todas as páginas", example = "12") long totalElementos,
        @Schema(description = "Total de páginas", example = "2") int totalPaginas,
        @Schema(example = "false") boolean ultima
) {
    public static <E, T> PaginaResponse<T> de(Page<E> page, Function<E, T> conversor) {
        return new PaginaResponse<>(page.getContent().stream().map(conversor).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), page.isLast());
    }
}
