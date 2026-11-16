package br.com.fiap.ThermoCity.busca.service;

import br.com.fiap.ThermoCity.busca.dto.ListaItemResponseDTO;
import br.com.fiap.ThermoCity.busca.dto.ListaResponseDTO;
import br.com.fiap.ThermoCity.busca.model.ListaBuscaAtiva;
import br.com.fiap.ThermoCity.busca.model.ListaItem;
import br.com.fiap.ThermoCity.busca.model.StatusLista;
import br.com.fiap.ThermoCity.busca.repository.ListaBuscaAtivaRepository;
import br.com.fiap.ThermoCity.busca.repository.ListaItemRepository;
import br.com.fiap.ThermoCity.common.PaginaResponse;
import br.com.fiap.ThermoCity.common.Paginacao;
import br.com.fiap.ThermoCity.exception.AcessoNegadoException;
import br.com.fiap.ThermoCity.territorio.service.GeoService;
import br.com.fiap.ThermoCity.territorio.service.RefugioProximo;
import br.com.fiap.ThermoCity.user.model.User;
import br.com.fiap.ThermoCity.user.model.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Consulta da lista priorizada: o agente vê a lista da sua UBS; o gestor vê qualquer UBS ou todas. */
@Service
public class ListaConsultaService {

    private final ListaBuscaAtivaRepository listaRepository;
    private final ListaItemRepository itemRepository;
    private final GeoService geoService;
    private final Clock clock;

    public ListaConsultaService(ListaBuscaAtivaRepository listaRepository, ListaItemRepository itemRepository,
                                GeoService geoService, Clock clock) {
        this.listaRepository = listaRepository;
        this.itemRepository = itemRepository;
        this.geoService = geoService;
        this.clock = clock;
    }

    public LocalDate hoje() {
        return LocalDate.now(clock);
    }

    /** Vazio quando não há lista aberta na data (NC1/NC2): o controller responde 204. */
    @Transactional(readOnly = true)
    public Optional<ListaResponseDTO> consultar(User usuario, LocalDate data, String ubsInformada, int pagina,
                                                int tamanho) {
        String ubsId = ubsPermitida(usuario, ubsInformada);
        var paginacao = Paginacao.de(pagina, tamanho, Sort.unsorted());

        List<ListaBuscaAtiva> listas = ubsId != null
                ? listaRepository.findByDataListaAndUbs_IdAndStatus(data, ubsId, StatusLista.ABERTA).stream().toList()
                : listaRepository.findByDataListaAndStatus(data, StatusLista.ABERTA);
        if (listas.isEmpty()) {
            return Optional.empty();
        }

        Page<ListaItem> itens = itemRepository.buscarDasListas(listas.stream().map(ListaBuscaAtiva::getId).toList(),
                paginacao);
        PaginaResponse<ListaItemResponseDTO> conteudo = PaginaResponse.de(itens,
                item -> ListaItemResponseDTO.from(item, data, refugio(item)));

        ListaBuscaAtiva primeira = listas.getFirst();
        int nivel = listas.stream().mapToInt(ListaBuscaAtiva::getNivelCalor).max().orElse(primeira.getNivelCalor());
        boolean umaUbs = listas.size() == 1 || ubsId != null;
        return Optional.of(new ListaResponseDTO(data, nivel, primeira.getVersaoRegra(),
                umaUbs ? primeira.getUbs().getId() : null, umaUbs ? primeira.getUbs().getNome() : null,
                listas.size(), conteudo));
    }

    /** O agente só consulta a própria UBS (sem informar, é a dele); o gestor consulta qualquer uma ou todas. */
    private String ubsPermitida(User usuario, String ubsInformada) {
        String ubs = ubsInformada == null || ubsInformada.isBlank() ? null : ubsInformada.trim();
        if (usuario.getPapel() != UserRole.AGENTE) {
            return ubs;
        }
        if (usuario.getUbsId() == null) {
            throw new AcessoNegadoException("Agente sem UBS vinculada");
        }
        if (ubs != null && !ubs.equals(usuario.getUbsId())) {
            throw new AcessoNegadoException("O agente só consulta a lista da sua UBS");
        }
        return usuario.getUbsId();
    }

    private RefugioProximo refugio(ListaItem item) {
        if (item.getPessoa() == null || item.getPessoa().getLatitude() == null || item.getPessoa().getLongitude() == null) {
            return null;
        }
        return geoService.refugioMaisProximo(item.getPessoa().getLatitude(), item.getPessoa().getLongitude())
                .orElse(null);
    }
}
