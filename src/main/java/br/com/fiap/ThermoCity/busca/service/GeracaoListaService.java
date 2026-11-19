package br.com.fiap.ThermoCity.busca.service;

import br.com.fiap.ThermoCity.busca.evento.EventoNivelCalor;
import br.com.fiap.ThermoCity.busca.model.AcaoEvento;
import br.com.fiap.ThermoCity.busca.model.EventoProcessado;
import br.com.fiap.ThermoCity.busca.model.ListaBuscaAtiva;
import br.com.fiap.ThermoCity.busca.model.StatusLista;
import br.com.fiap.ThermoCity.busca.repository.EventoProcessadoRepository;
import br.com.fiap.ThermoCity.busca.repository.ListaBuscaAtivaRepository;
import br.com.fiap.ThermoCity.busca.repository.PriorizacaoBancoRepository;
import br.com.fiap.ThermoCity.busca.service.PrioridadeService.Avaliacao;
import br.com.fiap.ThermoCity.pessoa.model.Pessoa;
import br.com.fiap.ThermoCity.pessoa.repository.PessoaRepository;
import br.com.fiap.ThermoCity.territorio.model.CalorSetor;
import br.com.fiap.ThermoCity.territorio.model.Servico;
import br.com.fiap.ThermoCity.territorio.repository.CalorSetorRepository;
import br.com.fiap.ThermoCity.territorio.service.GeoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reage ao nível de calor de um dia: a partir de NC3 gera (ou amplia) uma lista por UBS com as pessoas
 * priorizadas pela regra v1; abaixo de NC3 encerra as listas abertas do dia.
 *
 * Regras de repetição:
 * - o mesmo idEvento só é processado uma vez (tmc_evento_processado);
 * - existe no máximo uma lista por UBS por dia; um novo evento para o mesmo dia atualiza essa lista,
 *   acrescentando quem passou a entrar no alcance e preservando o status dos contatos já feitos;
 * - a ordem é recalculada pela pontuação a cada atualização.
 *
 * Onde a priorização roda (thermocity.busca.priorizacao):
 * - "banco" (padrão): a procedure tmc_prc_gerar_lista_busca_ativa monta as listas no PostgreSQL
 *   (scripts/script-priorizacao.sql). A trigger tmc_trg_pessoa_reorganiza_lista chama a mesma procedure
 *   quando o cadastro de pessoas muda, então a lista do dia se reorganiza sozinha.
 * - "java": a mesma regra v1 em Java (PrioridadeService). Usada nos testes, que rodam em H2, sem PL/pgSQL.
 * A idempotência do evento e o encerramento das listas (abaixo de NC3) ficam aqui nos dois modos.
 */
@Service
public class GeracaoListaService {

    public static final int NIVEL_MINIMO_BUSCA = 3;

    private static final Logger log = LoggerFactory.getLogger(GeracaoListaService.class);
    /** Afasta as posições atuais antes de renumerar, para não violar a unicidade (lista, posição). */
    private static final int DESLOCAMENTO_POSICAO = 1_000_000;
    private static final int TAMANHO_MOTIVOS = 500;

    private final ListaBuscaAtivaRepository listaRepository;
    private final EventoProcessadoRepository eventoRepository;
    private final PessoaRepository pessoaRepository;
    private final CalorSetorRepository calorSetorRepository;
    private final GeoService geoService;
    private final PrioridadeService prioridadeService;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final PriorizacaoBancoRepository priorizacaoBanco;
    private final boolean priorizacaoNoBanco;
    private final Set<String> ubsExcluidas;

    public GeracaoListaService(ListaBuscaAtivaRepository listaRepository, EventoProcessadoRepository eventoRepository,
                               PessoaRepository pessoaRepository, CalorSetorRepository calorSetorRepository,
                               GeoService geoService, PrioridadeService prioridadeService, JdbcTemplate jdbc,
                               Clock clock, PriorizacaoBancoRepository priorizacaoBanco,
                               @Value("${thermocity.busca.priorizacao:banco}") String priorizacao,
                               @Value("${thermocity.ubs.excluidas:}") Set<String> ubsExcluidas) {
        this.listaRepository = listaRepository;
        this.eventoRepository = eventoRepository;
        this.pessoaRepository = pessoaRepository;
        this.calorSetorRepository = calorSetorRepository;
        this.geoService = geoService;
        this.prioridadeService = prioridadeService;
        this.jdbc = jdbc;
        this.clock = clock;
        this.priorizacaoBanco = priorizacaoBanco;
        this.priorizacaoNoBanco = !"java".equalsIgnoreCase(priorizacao);
        this.ubsExcluidas = ubsExcluidas;
    }

    /** O evento precisa estar válido (EventoNivelCalor.erros() vazio). */
    @Transactional
    public ResultadoProcessamento processar(EventoNivelCalor evento) {
        if (eventoRepository.existsById(evento.idEvento())) {
            log.info("Evento {} já processado: ignorado", evento.idEvento());
            return ResultadoProcessamento.semListas(evento.idEvento(), evento.dataLocal(), evento.nivelNovo(),
                    AcaoEvento.DUPLICADO, 0);
        }

        ResultadoProcessamento resultado = evento.nivelNovo() >= NIVEL_MINIMO_BUSCA ? gerar(evento) : encerrar(evento);

        eventoRepository.save(new EventoProcessado(evento.idEvento(), evento.dataLocal(), evento.nivelNovo(),
                resultado.acao(), resultado.listasAfetadas(), OffsetDateTime.now(clock)));
        log.info("Evento {} (NC{} em {}): {} em {} listas, {} pessoas incluídas, {} no total",
                evento.idEvento(), evento.nivelNovo(), evento.dataLocal(), resultado.acao(),
                resultado.listasAfetadas(), resultado.pessoasIncluidas(), resultado.totalNasListas());
        return resultado;
    }

    private ResultadoProcessamento encerrar(EventoNivelCalor evento) {
        List<ListaBuscaAtiva> abertas = listaRepository.findByDataListaAndStatus(evento.dataLocal(), StatusLista.ABERTA);
        abertas.forEach(ListaBuscaAtiva::encerrar);
        AcaoEvento acao = abertas.isEmpty() ? AcaoEvento.SEM_ACAO : AcaoEvento.LISTAS_ENCERRADAS;
        return ResultadoProcessamento.semListas(evento.idEvento(), evento.dataLocal(), evento.nivelNovo(), acao,
                abertas.size());
    }

    private ResultadoProcessamento gerar(EventoNivelCalor evento) {
        return priorizacaoNoBanco ? gerarNoBanco(evento) : gerarEmJava(evento);
    }

    /** Priorização pela procedure do banco (modo padrão). */
    private ResultadoProcessamento gerarNoBanco(EventoNivelCalor evento) {
        PriorizacaoBancoRepository.Resultado r = priorizacaoBanco.gerar(evento.dataLocal(), evento.nivelNovo(),
                evento.idEvento(), ubsExcluidas, prioridadeService.versaoRegra());
        if (r.semCadastroCompleto() > 0) {
            log.warn("{} pessoas fora da busca ativa: sem setor ou sem UBS válida. Corrigir o cadastro.",
                    r.semCadastroCompleto());
        }
        return new ResultadoProcessamento(evento.idEvento(), evento.dataLocal(), evento.nivelNovo(),
                AcaoEvento.LISTAS_GERADAS, r.listasAfetadas(), r.pessoasIncluidas(), r.totalNasListas(),
                r.semCadastroCompleto());
    }

    /** Priorização em Java (testes em H2). Mesma regra e mesmo resultado da procedure. */
    private ResultadoProcessamento gerarEmJava(EventoNivelCalor evento) {
        LocalDate data = evento.dataLocal();
        int nivel = evento.nivelNovo();
        List<Servico> ubsValidas = geoService.ubsValidas();
        Set<String> idsUbsValidas = ubsValidas.stream().map(Servico::getId).collect(Collectors.toSet());
        Map<String, Integer> notaCalor = calorSetorRepository.findAll().stream()
                .filter(c -> c.getClasseCalor() != null)
                .collect(Collectors.toMap(CalorSetor::getCodigoSetor, CalorSetor::getClasseCalor));

        Map<String, List<Candidato>> candidatosPorUbs = new HashMap<>();
        int semCadastroCompleto = 0;
        for (Pessoa pessoa : pessoaRepository.findParaBuscaAtiva()) {
            if (pessoa.getSetor() == null || pessoa.getUbs() == null || !idsUbsValidas.contains(pessoa.getUbs().getId())) {
                semCadastroCompleto++;
                log.warn("Pessoa {} fora da busca ativa: sem setor ou sem UBS válida. Corrigir o cadastro.",
                        pessoa.getId());
                continue;
            }
            prioridadeService.avaliar(pessoa, nivel, data, notaCalor).ifPresent(avaliacao ->
                    candidatosPorUbs.computeIfAbsent(pessoa.getUbs().getId(), id -> new ArrayList<>())
                            .add(new Candidato(pessoa.getId(), pessoa.getSetor().getCodigo(), avaliacao)));
        }

        Map<String, ListaBuscaAtiva> listasDoDia = listaRepository.findByDataLista(data).stream()
                .collect(Collectors.toMap(l -> l.getUbs().getId(), Function.identity()));

        int incluidas = 0;
        int total = 0;
        for (Servico ubs : ubsValidas) {
            ListaBuscaAtiva lista = listasDoDia.get(ubs.getId());
            if (lista == null) {
                lista = listaRepository.save(new ListaBuscaAtiva(data, ubs, nivel, evento.idEvento(),
                        prioridadeService.versaoRegra()));
            } else {
                if (!lista.isAberta()) {
                    lista.reabrir();
                }
                lista.elevarNivel(nivel);
            }
            Contagem contagem = montarItens(lista.getId(), candidatosPorUbs.getOrDefault(ubs.getId(), List.of()));
            incluidas += contagem.novos();
            total += contagem.total();
        }
        listaRepository.flush();

        return new ResultadoProcessamento(evento.idEvento(), data, nivel, AcaoEvento.LISTAS_GERADAS, ubsValidas.size(),
                incluidas, total, semCadastroCompleto);
    }

    /**
     * Junta quem já está na lista com os candidatos do nível atual e renumera tudo pela pontuação.
     * Usa JDBC em lote: no NC5 são milhares de itens, e o id IDENTITY impede o lote pelo Hibernate.
     */
    private Contagem montarItens(Long listaId, List<Candidato> candidatos) {
        List<ItemAtual> atuais = jdbc.query(
                "select id, pessoa_id, setor_codigo, pontuacao, motivos from tmc_lista_item where lista_id = ?",
                (rs, n) -> new ItemAtual(rs.getLong("id"), (Long) rs.getObject("pessoa_id", Long.class),
                        rs.getString("setor_codigo"), rs.getInt("pontuacao"), rs.getString("motivos")),
                listaId);
        Map<Long, ItemAtual> atualPorPessoa = new HashMap<>();
        for (ItemAtual item : atuais) {
            if (item.pessoaId() != null) {
                atualPorPessoa.put(item.pessoaId(), item);
            }
        }

        List<Linha> linhas = new ArrayList<>();
        Set<Long> itensAtualizados = new HashSet<>();
        for (Candidato c : candidatos) {
            ItemAtual atual = atualPorPessoa.get(c.pessoaId());
            Long itemId = atual != null ? atual.id() : null;
            if (itemId != null) {
                itensAtualizados.add(itemId);
            }
            linhas.add(new Linha(itemId, c.pessoaId(), c.setorCodigo(), c.avaliacao().pontuacao(),
                    limitar(c.avaliacao().motivos()), c.avaliacao().idade()));
        }
        // Quem já estava na lista e saiu do alcance (nível caiu ou cadastro mudou) continua, com a pontuação antiga
        for (ItemAtual item : atuais) {
            if (!itensAtualizados.contains(item.id())) {
                linhas.add(new Linha(item.id(), item.pessoaId(), item.setorCodigo(), item.pontuacao(), item.motivos(), 0));
            }
        }

        linhas.sort(Comparator.comparingInt(Linha::pontuacao).reversed()
                .thenComparing(Comparator.comparingInt(Linha::idade).reversed())
                .thenComparing(Linha::pessoaId, Comparator.nullsLast(Comparator.naturalOrder())));

        List<Object[]> inserir = new ArrayList<>();
        List<Object[]> atualizar = new ArrayList<>();
        for (int i = 0; i < linhas.size(); i++) {
            Linha l = linhas.get(i);
            int posicao = i + 1;
            if (l.itemId() == null) {
                inserir.add(new Object[]{listaId, l.pessoaId(), l.setorCodigo(), posicao, l.pontuacao(), l.motivos()});
            } else {
                atualizar.add(new Object[]{posicao, l.pontuacao(), l.motivos(), l.itemId()});
            }
        }

        if (!atuais.isEmpty()) {
            jdbc.update("update tmc_lista_item set posicao = posicao + ? where lista_id = ?", DESLOCAMENTO_POSICAO, listaId);
            jdbc.batchUpdate("update tmc_lista_item set posicao = ?, pontuacao = ?, motivos = ? where id = ?", atualizar);
        }
        if (!inserir.isEmpty()) {
            jdbc.batchUpdate("insert into tmc_lista_item (lista_id, pessoa_id, setor_codigo, posicao, pontuacao, motivos, "
                    + "status_contato) values (?, ?, ?, ?, ?, ?, 'PENDENTE')", inserir);
        }
        return new Contagem(inserir.size(), linhas.size());
    }

    private static String limitar(String motivos) {
        return motivos.length() <= TAMANHO_MOTIVOS ? motivos : motivos.substring(0, TAMANHO_MOTIVOS);
    }

    private record Candidato(Long pessoaId, String setorCodigo, Avaliacao avaliacao) {
    }

    private record ItemAtual(Long id, Long pessoaId, String setorCodigo, int pontuacao, String motivos) {
    }

    private record Linha(Long itemId, Long pessoaId, String setorCodigo, int pontuacao, String motivos, int idade) {
    }

    private record Contagem(int novos, int total) {
    }
}
