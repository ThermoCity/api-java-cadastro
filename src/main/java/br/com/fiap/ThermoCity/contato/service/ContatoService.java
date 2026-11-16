package br.com.fiap.ThermoCity.contato.service;

import br.com.fiap.ThermoCity.busca.model.ListaItem;
import br.com.fiap.ThermoCity.busca.model.StatusContato;
import br.com.fiap.ThermoCity.busca.repository.ListaItemRepository;
import br.com.fiap.ThermoCity.common.Paginacao;
import br.com.fiap.ThermoCity.contato.dto.ContatoRequestDTO;
import br.com.fiap.ThermoCity.contato.model.Contato;
import br.com.fiap.ThermoCity.contato.model.ResultadoContato;
import br.com.fiap.ThermoCity.contato.repository.ContatoRepository;
import br.com.fiap.ThermoCity.exception.AcessoNegadoException;
import br.com.fiap.ThermoCity.exception.RecursoNaoEncontradoException;
import br.com.fiap.ThermoCity.exception.ValorNaoProcessavelException;
import br.com.fiap.ThermoCity.pessoa.model.Pessoa;
import br.com.fiap.ThermoCity.pessoa.repository.PessoaRepository;
import br.com.fiap.ThermoCity.pessoa.service.PessoaService;
import br.com.fiap.ThermoCity.user.model.User;
import br.com.fiap.ThermoCity.user.model.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Service
public class ContatoService {

    public static final String MSG_RESULTADO_INVALIDO = "Resultado de contato inválido";
    public static final String MSG_OUTRA_UBS = "A pessoa não pertence à sua UBS";
    public static final String MSG_AGENTE_SEM_UBS = "Agente sem UBS vinculada";

    private static final Sort ORDEM_RECENTES = Sort.by(Sort.Order.desc("realizadoEm"), Sort.Order.desc("id"));

    private final ContatoRepository contatoRepository;
    private final PessoaRepository pessoaRepository;
    private final ListaItemRepository listaItemRepository;
    private final Clock clock;

    public ContatoService(ContatoRepository contatoRepository, PessoaRepository pessoaRepository,
                          ListaItemRepository listaItemRepository, Clock clock) {
        this.contatoRepository = contatoRepository;
        this.pessoaRepository = pessoaRepository;
        this.listaItemRepository = listaItemRepository;
        this.clock = clock;
    }

    /** Registra o contato e, se a pessoa está na lista aberta do dia, atualiza a situação dela na lista. */
    @Transactional
    public Contato registrar(User agente, ContatoRequestDTO dto) {
        ResultadoContato resultado = converterResultado(dto.resultado());
        Pessoa pessoa = pessoaRepository.findComRelacionamentosById(dto.pessoaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(PessoaService.MSG_NAO_ENCONTRADA));
        exigirMesmaUbs(agente, pessoa);

        OffsetDateTime agora = OffsetDateTime.now(clock);
        ListaItem item = listaItemRepository.findItemAberto(pessoa.getId(), LocalDate.now(clock)).orElse(null);
        if (item != null) {
            item.registrarContato(StatusContato.de(resultado), agora);
        }
        String observacao = dto.observacao() == null || dto.observacao().isBlank() ? null : dto.observacao().trim();
        return contatoRepository.save(
                new Contato(pessoa, item, agente, pessoa.getUbs(), resultado, observacao, agora));
    }

    /** AGENTE vê os contatos da sua UBS; GESTOR vê todos. Ordenado do mais recente para o mais antigo. */
    @Transactional(readOnly = true)
    public Page<Contato> listar(User usuario, Long pessoaId, String resultado, int pagina, int tamanho) {
        ResultadoContato filtroResultado = resultado == null || resultado.isBlank()
                ? null : converterResultado(resultado);
        String ubsId = null;
        if (usuario.getPapel() == UserRole.AGENTE) {
            ubsId = ubsDoAgente(usuario);
        }
        if (pessoaId != null) {
            Pessoa pessoa = pessoaRepository.findComRelacionamentosById(pessoaId)
                    .orElseThrow(() -> new RecursoNaoEncontradoException(PessoaService.MSG_NAO_ENCONTRADA));
            if (usuario.getPapel() == UserRole.AGENTE) {
                exigirMesmaUbs(usuario, pessoa);
            }
        }
        return contatoRepository.buscar(pessoaId, ubsId, filtroResultado,
                Paginacao.de(pagina, tamanho, ORDEM_RECENTES));
    }

    @Transactional(readOnly = true)
    public Contato buscar(User usuario, Long id) {
        Contato contato = contatoRepository.findComRelacionamentosById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Contato não encontrado"));
        if (usuario.getPapel() == UserRole.AGENTE && !contato.getUbs().getId().equals(ubsDoAgente(usuario))) {
            throw new AcessoNegadoException("O contato é de outra UBS");
        }
        return contato;
    }

    private static ResultadoContato converterResultado(String valor) {
        return ResultadoContato.de(valor).orElseThrow(() ->
                new ValorNaoProcessavelException(MSG_RESULTADO_INVALIDO, ResultadoContato.valoresValidos()));
    }

    private static void exigirMesmaUbs(User agente, Pessoa pessoa) {
        if (!ubsDoAgente(agente).equals(pessoa.getUbsId())) {
            throw new AcessoNegadoException(MSG_OUTRA_UBS);
        }
    }

    private static String ubsDoAgente(User agente) {
        if (agente.getUbsId() == null) {
            throw new AcessoNegadoException(MSG_AGENTE_SEM_UBS);
        }
        return agente.getUbsId();
    }
}
