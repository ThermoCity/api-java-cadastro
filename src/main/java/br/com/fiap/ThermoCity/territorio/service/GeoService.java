package br.com.fiap.ThermoCity.territorio.service;

import br.com.fiap.ThermoCity.common.Distancia;
import br.com.fiap.ThermoCity.territorio.model.Servico;
import br.com.fiap.ThermoCity.territorio.model.Setor;
import br.com.fiap.ThermoCity.territorio.repository.ServicoRepository;
import br.com.fiap.ThermoCity.territorio.repository.SetorRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.locationtech.jts.index.strtree.STRtree;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Define setor censitário e UBS de referência a partir de uma coordenada.
 * Os 420 polígonos de tmc_setor são carregados uma vez e indexados em memória (JTS STRtree),
 * porque o banco não tem PostGIS. A área atendida é a união desses setores: coordenada fora de
 * todos eles está fora de Presidente Prudente.
 */
@Service
public class GeoService {

    private static final Logger log = LoggerFactory.getLogger(GeoService.class);

    private final SetorRepository setorRepository;
    private final ServicoRepository servicoRepository;
    private final Set<String> ubsExcluidas;
    private final Set<String> revisoesRefugio;
    private final GeometryFactory geometryFactory = new GeometryFactory();

    private volatile Indice indice;

    public GeoService(SetorRepository setorRepository, ServicoRepository servicoRepository,
                      @Value("${thermocity.ubs.excluidas:}") Set<String> ubsExcluidas,
                      @Value("${thermocity.refugios.revisoes:manter,potencial}") Set<String> revisoesRefugio) {
        this.setorRepository = setorRepository;
        this.servicoRepository = servicoRepository;
        this.ubsExcluidas = ubsExcluidas;
        this.revisoesRefugio = revisoesRefugio;
    }

    /** UBS que podem ser referência (sem penitenciária, triagem COVID etc.), em ordem de nome. */
    public List<Servico> ubsValidas() {
        return indice().ubs();
    }

    /**
     * Refúgio climático mais próximo da coordenada. Só valem os refúgios cuja revisão está em
     * thermocity.refugios.revisoes (manter e potencial: 101 locais no banco atual).
     */
    public Optional<RefugioProximo> refugioMaisProximo(double latitude, double longitude) {
        return indice().refugios().stream()
                .map(r -> new RefugioProximo(r, Distancia.metros(latitude, longitude, r.getLat(), r.getLon())))
                .min(Comparator.comparingDouble(RefugioProximo::distanciaMetros));
    }

    /** Setor que contém a coordenada e UBS válida mais próxima; vazio se estiver fora da área atendida. */
    public Optional<Localizacao> localizar(double latitude, double longitude) {
        Indice atual = indice();
        Point ponto = geometryFactory.createPoint(new Coordinate(longitude, latitude));

        @SuppressWarnings("unchecked")
        List<SetorIndexado> candidatos = atual.arvore().query(ponto.getEnvelopeInternal());
        Optional<Setor> setor = candidatos.stream()
                .filter(c -> c.geometria().covers(ponto))
                .map(SetorIndexado::setor)
                .findFirst();

        return setor.flatMap(s -> ubsMaisProxima(atual.ubs(), latitude, longitude)
                .map(ubs -> new Localizacao(s, ubs,
                        Distancia.metros(latitude, longitude, ubs.getLat(), ubs.getLon()))));
    }

    /** true se o id é de uma UBS que pode ser referência (exclui penitenciária, triagem COVID etc.). */
    public boolean isUbsValida(String ubsId) {
        return ubsId != null && indice().ubs().stream().anyMatch(u -> u.getId().equals(ubsId));
    }

    /** Descarta o índice em memória; o próximo acesso recarrega setores e UBS do banco. */
    public void recarregar() {
        indice = null;
    }

    private Optional<Servico> ubsMaisProxima(List<Servico> ubs, double latitude, double longitude) {
        return ubs.stream().min(Comparator.comparingDouble(
                u -> Distancia.metros(latitude, longitude, u.getLat(), u.getLon())));
    }

    private Indice indice() {
        Indice atual = indice;
        if (atual == null) {
            synchronized (this) {
                atual = indice;
                if (atual == null) {
                    atual = carregar();
                    indice = atual;
                }
            }
        }
        return atual;
    }

    private Indice carregar() {
        WKTReader leitor = new WKTReader(geometryFactory);
        STRtree arvore = new STRtree();
        int carregados = 0;
        for (Setor setor : setorRepository.findAll()) {
            if (setor.getGeometriaWkt() == null) {
                continue;
            }
            try {
                Geometry geometria = leitor.read(setor.getGeometriaWkt());
                PreparedGeometry preparada = PreparedGeometryFactory.prepare(geometria);
                arvore.insert(geometria.getEnvelopeInternal(), new SetorIndexado(setor, preparada));
                carregados++;
            } catch (ParseException e) {
                log.warn("Geometria inválida no setor {}: {}", setor.getCodigo(), e.getMessage());
            }
        }
        arvore.build();

        List<Servico> ubs = new ArrayList<>();
        for (Servico servico : servicoRepository.findByTipo(Servico.TIPO_UBS)) {
            if (!ubsExcluidas.contains(servico.getId()) && servico.getLat() != null && servico.getLon() != null) {
                ubs.add(servico);
            }
        }
        ubs.sort(Comparator.comparing(Servico::getNome, Comparator.nullsLast(Comparator.naturalOrder())));

        List<Servico> refugios = new ArrayList<>();
        for (Servico servico : servicoRepository.findByTipo(Servico.TIPO_REFUGIO)) {
            if (servico.getRevisao() != null && revisoesRefugio.contains(servico.getRevisao())
                    && servico.getLat() != null && servico.getLon() != null) {
                refugios.add(servico);
            }
        }
        log.info("Índice territorial carregado: {} setores, {} UBS válidas e {} refúgios", carregados, ubs.size(),
                refugios.size());
        return new Indice(arvore, List.copyOf(ubs), List.copyOf(refugios));
    }

    private record SetorIndexado(Setor setor, PreparedGeometry geometria) {
    }

    private record Indice(STRtree arvore, List<Servico> ubs, List<Servico> refugios) {
    }
}
