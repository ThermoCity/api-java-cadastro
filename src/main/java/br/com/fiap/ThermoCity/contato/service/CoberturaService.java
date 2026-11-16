package br.com.fiap.ThermoCity.contato.service;

import br.com.fiap.ThermoCity.busca.model.StatusContato;
import br.com.fiap.ThermoCity.busca.repository.CoberturaLinha;
import br.com.fiap.ThermoCity.busca.repository.ListaBuscaAtivaRepository;
import br.com.fiap.ThermoCity.busca.repository.ListaItemRepository;
import br.com.fiap.ThermoCity.contato.dto.CoberturaResponseDTO;
import br.com.fiap.ThermoCity.contato.dto.CoberturaResponseDTO.CoberturaSetor;
import br.com.fiap.ThermoCity.contato.dto.CoberturaResponseDTO.CoberturaUbs;
import br.com.fiap.ThermoCity.contato.dto.CoberturaResponseDTO.Resumo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class CoberturaService {

    private final ListaItemRepository listaItemRepository;
    private final ListaBuscaAtivaRepository listaRepository;
    private final Clock clock;

    public CoberturaService(ListaItemRepository listaItemRepository, ListaBuscaAtivaRepository listaRepository,
                            Clock clock) {
        this.listaItemRepository = listaItemRepository;
        this.listaRepository = listaRepository;
        this.clock = clock;
    }

    /** Vazio quando não há lista no dia (NC1/NC2): o controller responde 204. */
    @Transactional(readOnly = true)
    public Optional<CoberturaResponseDTO> doDia() {
        LocalDate hoje = LocalDate.now(clock);
        List<CoberturaLinha> linhas = listaItemRepository.contarPorUbsSetorStatus(hoje);
        if (linhas.isEmpty()) {
            return Optional.empty();
        }

        Contador geral = new Contador();
        Map<String, UbsAcumulada> porUbs = new LinkedHashMap<>();
        for (CoberturaLinha linha : linhas) {
            geral.somar(linha.status(), linha.quantidade());
            UbsAcumulada ubs = porUbs.computeIfAbsent(linha.ubsId(), id -> new UbsAcumulada(id, linha.ubsNome()));
            ubs.contador.somar(linha.status(), linha.quantidade());
            ubs.setores.computeIfAbsent(linha.setorCodigo(), c -> new SetorAcumulado(c, linha.bairro()))
                    .contador.somar(linha.status(), linha.quantidade());
        }

        List<CoberturaUbs> ubs = new ArrayList<>();
        for (UbsAcumulada u : porUbs.values()) {
            List<CoberturaSetor> setores = u.setores.values().stream()
                    .map(s -> new CoberturaSetor(s.codigo, s.bairro, s.contador.resumo()))
                    .sorted(Comparator.comparing((CoberturaSetor s) -> s.resumo().coberturaPercentual())
                            .thenComparing(CoberturaSetor::setorCodigo))
                    .toList();
            ubs.add(new CoberturaUbs(u.id, u.nome, u.contador.resumo(), setores));
        }
        ubs.sort(Comparator.comparing((CoberturaUbs u) -> u.resumo().coberturaPercentual())
                .thenComparing(CoberturaUbs::ubsNome));

        Integer nivel = listaRepository.nivelDoDia(hoje).orElse(null);
        return Optional.of(new CoberturaResponseDTO(hoje, nivel, geral.resumo(), ubs));
    }

    private static final class Contador {
        private long total;
        private long contatados;
        private long pendentes;
        private long precisaAjuda;
        private long naoAtendeu;
        private long dadosIncorretos;

        void somar(StatusContato status, long quantidade) {
            total += quantidade;
            if (status.isContatado()) {
                contatados += quantidade;
            }
            switch (status) {
                case PENDENTE -> pendentes += quantidade;
                case NAO_ATENDEU -> {
                    naoAtendeu += quantidade;
                    pendentes += quantidade;
                }
                case PRECISA_AJUDA -> precisaAjuda += quantidade;
                case DADOS_INCORRETOS -> dadosIncorretos += quantidade;
                case CONTATADO_BEM -> {
                }
            }
        }

        Resumo resumo() {
            BigDecimal percentual = total == 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(contatados * 100L).divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
            return new Resumo(total, contatados, pendentes, precisaAjuda, naoAtendeu, dadosIncorretos, percentual);
        }
    }

    private static final class UbsAcumulada {
        private final String id;
        private final String nome;
        private final Contador contador = new Contador();
        private final Map<String, SetorAcumulado> setores = new LinkedHashMap<>();

        UbsAcumulada(String id, String nome) {
            this.id = id;
            this.nome = nome;
        }
    }

    private static final class SetorAcumulado {
        private final String codigo;
        private final String bairro;
        private final Contador contador = new Contador();

        SetorAcumulado(String codigo, String bairro) {
            this.codigo = codigo;
            this.bairro = bairro;
        }
    }
}
