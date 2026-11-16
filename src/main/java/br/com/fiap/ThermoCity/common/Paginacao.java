package br.com.fiap.ThermoCity.common;

import br.com.fiap.ThermoCity.exception.RegraNegocioException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/** Valida page/size vindos da query string e monta o PageRequest com ordenação fixa. */
public final class Paginacao {

    public static final int TAMANHO_MAXIMO = 100;

    private Paginacao() {
    }

    public static PageRequest de(int pagina, int tamanho, Sort ordem) {
        if (pagina < 0) {
            throw new RegraNegocioException("O parâmetro page deve ser maior ou igual a 0");
        }
        if (tamanho < 1 || tamanho > TAMANHO_MAXIMO) {
            throw new RegraNegocioException("O parâmetro size deve estar entre 1 e " + TAMANHO_MAXIMO);
        }
        return PageRequest.of(pagina, tamanho, ordem);
    }
}
