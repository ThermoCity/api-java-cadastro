package br.com.fiap.ThermoCity.pessoa.service;

import br.com.fiap.ThermoCity.busca.repository.ListaItemRepository;
import br.com.fiap.ThermoCity.common.Paginacao;
import br.com.fiap.ThermoCity.contato.repository.ContatoRepository;
import br.com.fiap.ThermoCity.exception.AcessoNegadoException;
import br.com.fiap.ThermoCity.exception.RecursoNaoEncontradoException;
import br.com.fiap.ThermoCity.exception.RegraNegocioException;
import br.com.fiap.ThermoCity.pessoa.dto.PessoaRequestDTO;
import br.com.fiap.ThermoCity.pessoa.model.Pessoa;
import br.com.fiap.ThermoCity.pessoa.model.Pessoa.DadosPessoa;
import br.com.fiap.ThermoCity.pessoa.repository.OrientacaoContatoRepository;
import br.com.fiap.ThermoCity.pessoa.repository.PessoaRepository;
import br.com.fiap.ThermoCity.territorio.service.GeoService;
import br.com.fiap.ThermoCity.territorio.service.Localizacao;
import br.com.fiap.ThermoCity.user.model.User;
import br.com.fiap.ThermoCity.user.model.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.Locale;

@Service
public class PessoaService {

    public static final String MSG_NAO_ENCONTRADA = "Pessoa não encontrada";
    public static final String MSG_SEM_CONSENTIMENTO = "É necessário o consentimento para o cadastro";
    public static final String MSG_MENOR_60 = "O acompanhamento é para pessoas com 60 anos ou mais";
    public static final String MSG_FORA_DA_AREA = "Endereço fora da área atendida";
    public static final String MSG_SO_TITULAR = "Somente o titular do cadastro pode alterá-lo ou excluí-lo";

    private static final Logger log = LoggerFactory.getLogger(PessoaService.class);
    private static final Sort ORDEM_NOME = Sort.by("nome", "id");

    private final PessoaRepository pessoaRepository;
    private final ContatoRepository contatoRepository;
    private final ListaItemRepository listaItemRepository;
    private final OrientacaoContatoRepository orientacaoRepository;
    private final GeoService geoService;
    private final Clock clock;

    public PessoaService(PessoaRepository pessoaRepository, ContatoRepository contatoRepository,
                         ListaItemRepository listaItemRepository, OrientacaoContatoRepository orientacaoRepository,
                         GeoService geoService, Clock clock) {
        this.pessoaRepository = pessoaRepository;
        this.contatoRepository = contatoRepository;
        this.listaItemRepository = listaItemRepository;
        this.orientacaoRepository = orientacaoRepository;
        this.geoService = geoService;
        this.clock = clock;
    }

    public LocalDate hoje() {
        return LocalDate.now(clock);
    }

    @Transactional
    public Pessoa cadastrar(User titular, PessoaRequestDTO dto) {
        DadosPessoa dados = validarEConverter(dto);
        Localizacao localizacao = localizar(dados);
        Pessoa pessoa = Pessoa.cadastrar(titular, dados, hoje(), localizacao, agora());
        return pessoaRepository.save(pessoa);
    }

    /**
     * CIDADAO vê só as pessoas que cadastrou; AGENTE vê as da sua UBS (pedir outra UBS dá 403);
     * GESTOR vê todas e pode filtrar por UBS ou setor.
     */
    @Transactional(readOnly = true)
    public Page<Pessoa> listar(User usuario, String ubsId, String setorCodigo, String nome, int pagina, int tamanho) {
        Long titularId = null;
        String ubsFiltro = vazioParaNulo(ubsId);
        switch (usuario.getPapel()) {
            case CIDADAO -> titularId = usuario.getIdUsuario();
            case AGENTE -> {
                if (ubsFiltro != null && !ubsFiltro.equals(usuario.getUbsId())) {
                    throw new AcessoNegadoException("O agente só consulta pessoas da sua UBS");
                }
                ubsFiltro = usuario.getUbsId();
            }
            case GESTOR -> {
            }
            default -> throw new AcessoNegadoException("Acesso negado para o seu perfil");
        }
        String nomeFiltro = vazioParaNulo(nome);
        String padraoNome = nomeFiltro == null ? null : "%" + nomeFiltro.toLowerCase(Locale.ROOT) + "%";
        return pessoaRepository.buscar(titularId, ubsFiltro, vazioParaNulo(setorCodigo), padraoNome,
                Paginacao.de(pagina, tamanho, ORDEM_NOME));
    }

    @Transactional(readOnly = true)
    public Pessoa buscar(User usuario, Long id) {
        Pessoa pessoa = encontrar(id);
        boolean podeVer = switch (usuario.getPapel()) {
            case CIDADAO -> pessoa.isTitular(usuario);
            case AGENTE -> usuario.getUbsId() != null && usuario.getUbsId().equals(pessoa.getUbsId());
            case GESTOR -> true;
            default -> false;
        };
        if (!podeVer) {
            throw new AcessoNegadoException("Você não tem acesso a este cadastro");
        }
        return pessoa;
    }

    @Transactional
    public Pessoa atualizar(User usuario, Long id, PessoaRequestDTO dto) {
        Pessoa pessoa = encontrarDoTitular(usuario, id);
        DadosPessoa dados = validarEConverter(dto);
        Localizacao novaLocalizacao = pessoa.coordenadaDiferente(dados.latitude(), dados.longitude())
                ? localizar(dados)
                : null;
        pessoa.atualizar(dados, hoje(), novaLocalizacao, agora());
        return pessoa;
    }

    /**
     * Direito de eliminação (LGPD): apaga o cadastro e os roteiros gerados para a pessoa. Os contatos e os
     * itens de lista continuam existindo para a cobertura, mas sem vínculo com a pessoa e sem observação livre.
     */
    @Transactional
    public void excluir(User usuario, Long id) {
        Pessoa pessoa = encontrarDoTitular(usuario, id);
        int contatos = contatoRepository.anonimizarPorPessoa(id);
        int itens = listaItemRepository.desvincularPessoa(id);
        orientacaoRepository.apagarDaPessoa(id);
        pessoaRepository.delete(pessoa);
        log.info("Pessoa {} excluída pelo titular {}: {} contatos e {} itens de lista anonimizados",
                id, usuario.getIdUsuario(), contatos, itens);
    }

    private Pessoa encontrar(Long id) {
        return pessoaRepository.findComRelacionamentosById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException(MSG_NAO_ENCONTRADA));
    }

    private Pessoa encontrarDoTitular(User usuario, Long id) {
        Pessoa pessoa = encontrar(id);
        if (usuario.getPapel() != UserRole.CIDADAO || !pessoa.isTitular(usuario)) {
            throw new AcessoNegadoException(MSG_SO_TITULAR);
        }
        return pessoa;
    }

    /** Regras que o Bean Validation não cobre, verificadas antes de qualquer gravação. */
    private DadosPessoa validarEConverter(PessoaRequestDTO dto) {
        if (!Boolean.TRUE.equals(dto.consentimentoLgpd())) {
            throw new RegraNegocioException(MSG_SEM_CONSENTIMENTO);
        }
        if (Period.between(dto.dataNascimento(), hoje()).getYears() < Pessoa.IDADE_MINIMA) {
            throw new RegraNegocioException(MSG_MENOR_60);
        }
        return new DadosPessoa(
                dto.nome().trim(),
                dto.dataNascimento(),
                vazioParaNulo(dto.sexo()),
                somenteDigitos(dto.telefone()),
                dto.endereco().trim(),
                somenteDigitos(dto.cep()),
                dto.latitude(),
                dto.longitude(),
                Boolean.TRUE.equals(dto.moraSozinho()),
                Boolean.TRUE.equals(dto.mobilidadeReduzida()),
                Boolean.TRUE.equals(dto.condicaoCronica()),
                vazioParaNulo(dto.contatoReferenciaNome()),
                somenteDigitos(dto.contatoReferenciaTelefone()));
    }

    private Localizacao localizar(DadosPessoa dados) {
        return geoService.localizar(dados.latitude(), dados.longitude())
                .orElseThrow(() -> new RegraNegocioException(MSG_FORA_DA_AREA));
    }

    private OffsetDateTime agora() {
        return OffsetDateTime.now(clock);
    }

    private static String vazioParaNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private static String somenteDigitos(String valor) {
        String digitos = valor == null ? null : valor.replaceAll("\\D", "");
        return digitos == null || digitos.isEmpty() ? null : digitos;
    }
}
