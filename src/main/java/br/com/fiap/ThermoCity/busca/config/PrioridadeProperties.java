package br.com.fiap.ThermoCity.busca.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Regra de prioridade da busca ativa (thermocity.prioridade.*). Os pesos ficam em configuração para a
 * equipe de saúde ajustar sem mudar código; a versão da regra é gravada em cada lista gerada.
 */
@ConfigurationProperties("thermocity.prioridade")
public record PrioridadeProperties(
        @DefaultValue("v1") String versaoRegra,
        @DefaultValue Pesos pesos,
        @DefaultValue("800") int distanciaRefugioMetros,
        @DefaultValue AlcancePorNivel alcance
) {

    /** Pontos somados por critério. */
    public record Pesos(
            @DefaultValue("2") int idade70Mais,
            @DefaultValue("3") int moraSozinho,
            @DefaultValue("2") int mobilidadeReduzida,
            @DefaultValue("2") int condicaoCronica,
            /* pontos = (nota do setor - 1) x este peso; nota 1 não soma */
            @DefaultValue("1") int notaCalorSetor,
            @DefaultValue("2") int ipvs56,
            @DefaultValue("1") int ipvs4,
            @DefaultValue("1") int refugioDistante
    ) {
    }

    /** Quem entra na lista em cada nível: idade mínima e grupo IPVS mínimo do setor (0 = todos). */
    public record Alcance(int idadeMinima, int ipvsMinimo) {

        public boolean inclui(int idade, Integer ipvsGrupo) {
            if (idade < idadeMinima) {
                return false;
            }
            return ipvsMinimo <= 0 || (ipvsGrupo != null && ipvsGrupo >= ipvsMinimo);
        }
    }

    public record AlcancePorNivel(Alcance nc3, Alcance nc4, Alcance nc5) {

        public AlcancePorNivel {
            nc3 = nc3 != null ? nc3 : new Alcance(70, 5);
            nc4 = nc4 != null ? nc4 : new Alcance(60, 4);
            nc5 = nc5 != null ? nc5 : new Alcance(60, 0);
        }

        public Alcance doNivel(int nivel) {
            return switch (nivel) {
                case 3 -> nc3;
                case 4 -> nc4;
                case 5 -> nc5;
                default -> throw new IllegalArgumentException("Nível sem busca ativa: NC" + nivel);
            };
        }
    }
}
