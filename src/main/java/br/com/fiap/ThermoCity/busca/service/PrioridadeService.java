package br.com.fiap.ThermoCity.busca.service;

import br.com.fiap.ThermoCity.busca.config.PrioridadeProperties;
import br.com.fiap.ThermoCity.busca.config.PrioridadeProperties.Pesos;
import br.com.fiap.ThermoCity.pessoa.model.Pessoa;
import br.com.fiap.ThermoCity.territorio.service.GeoService;
import br.com.fiap.ThermoCity.territorio.service.RefugioProximo;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Regra de prioridade v1: decide se a pessoa entra na lista do nível de calor e calcula a pontuação,
 * com os motivos que a explicam (ex.: "mora sozinha · 78 anos · setor nota 5").
 */
@Service
public class PrioridadeService {

    private static final String SEPARADOR = " · ";

    private final PrioridadeProperties regra;
    private final GeoService geoService;

    public PrioridadeService(PrioridadeProperties regra, GeoService geoService) {
        this.regra = regra;
        this.geoService = geoService;
    }

    public String versaoRegra() {
        return regra.versaoRegra();
    }

    /**
     * Avalia a pessoa para o nível do dia. Vazio quando ela está fora do alcance do nível
     * (ex.: NC3 só inclui 70+ em setores IPVS 5 e 6). A pessoa precisa ter setor.
     *
     * @param notaCalorPorSetor nota de calor (1 a 5) por código de setor
     */
    public Optional<Avaliacao> avaliar(Pessoa pessoa, int nivel, LocalDate data, Map<String, Integer> notaCalorPorSetor) {
        int idade = idade(pessoa, data);
        Integer ipvs = pessoa.getSetor().getIpvsGrupo();
        if (!regra.alcance().doNivel(nivel).inclui(idade, ipvs)) {
            return Optional.empty();
        }

        Pesos pesos = regra.pesos();
        boolean feminino = "F".equalsIgnoreCase(pessoa.getSexo());
        int pontos = 0;
        List<String> motivos = new ArrayList<>();

        if (pessoa.isMoraSozinho()) {
            pontos += pesos.moraSozinho();
            motivos.add(feminino ? "mora sozinha" : "mora sozinho");
        }
        if (pessoa.isMobilidadeReduzida()) {
            pontos += pesos.mobilidadeReduzida();
            motivos.add("mobilidade reduzida");
        }
        if (pessoa.isCondicaoCronica()) {
            pontos += pesos.condicaoCronica();
            motivos.add("condição crônica");
        }

        if (idade >= 70) {
            pontos += pesos.idade70Mais();
        }
        motivos.add(idade + " anos");

        Integer nota = notaCalorPorSetor.get(pessoa.getSetor().getCodigo());
        if (nota != null) {
            pontos += Math.max(0, nota - 1) * pesos.notaCalorSetor();
            motivos.add("setor nota " + nota);
        }

        if (ipvs != null && ipvs >= 5) {
            pontos += pesos.ipvs56();
            motivos.add("IPVS " + ipvs);
        } else if (ipvs != null && ipvs == 4) {
            pontos += pesos.ipvs4();
            motivos.add("IPVS 4");
        }

        if (pessoa.getLatitude() != null && pessoa.getLongitude() != null) {
            Optional<RefugioProximo> refugio = geoService.refugioMaisProximo(pessoa.getLatitude(), pessoa.getLongitude());
            if (refugio.isEmpty()) {
                pontos += pesos.refugioDistante();
                motivos.add("sem refúgio cadastrado");
            } else if (refugio.get().distanciaMetros() > regra.distanciaRefugioMetros()) {
                pontos += pesos.refugioDistante();
                motivos.add("refúgio a " + distancia(refugio.get().distanciaMetros()));
            }
        }

        return Optional.of(new Avaliacao(pontos, String.join(SEPARADOR, motivos), idade));
    }

    /** Idade na data da lista: pela data de nascimento, senão a idade gravada, senão a faixa etária. */
    static int idade(Pessoa pessoa, LocalDate data) {
        Integer idade = pessoa.idadeEm(data);
        if (idade != null) {
            return idade;
        }
        return "70+".equals(pessoa.getFaixaEtaria()) ? 70 : Pessoa.IDADE_MINIMA;
    }

    private static String distancia(double metros) {
        return String.format(Locale.forLanguageTag("pt-BR"), "%.1f km", metros / 1000);
    }

    /** Resultado da regra para uma pessoa: pontuação, motivos e idade usada no desempate. */
    public record Avaliacao(int pontuacao, String motivos, int idade) {
    }
}
