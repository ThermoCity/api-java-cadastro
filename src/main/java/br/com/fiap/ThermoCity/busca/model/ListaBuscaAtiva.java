package br.com.fiap.ThermoCity.busca.model;

import br.com.fiap.ThermoCity.territorio.model.Servico;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Lista de busca ativa de uma UBS em um dia (gerada a partir de NC3). */
@Entity
@Table(name = "tmc_lista_busca_ativa",
        uniqueConstraints = @UniqueConstraint(name = "uk_tmc_lista_dia_ubs", columnNames = {"data_lista", "ubs_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ListaBuscaAtiva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "data_lista", nullable = false)
    private LocalDate dataLista;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ubs_id", nullable = false)
    private Servico ubs;

    @Column(name = "nivel_calor", nullable = false)
    private Integer nivelCalor;

    @Column(name = "evento_id", nullable = false, length = 64)
    private String eventoId;

    @Column(name = "versao_regra", nullable = false, length = 20)
    private String versaoRegra;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private StatusLista status;

    @Column(name = "criada_em", nullable = false)
    private OffsetDateTime criadaEm;

    @Column(name = "encerrada_em")
    private OffsetDateTime encerradaEm;

    public ListaBuscaAtiva(LocalDate dataLista, Servico ubs, int nivelCalor, String eventoId, String versaoRegra) {
        this.dataLista = dataLista;
        this.ubs = ubs;
        this.nivelCalor = nivelCalor;
        this.eventoId = eventoId;
        this.versaoRegra = versaoRegra;
        this.status = StatusLista.ABERTA;
        this.criadaEm = OffsetDateTime.now();
    }

    public void encerrar() {
        if (status == StatusLista.ABERTA) {
            status = StatusLista.ENCERRADA;
            encerradaEm = OffsetDateTime.now();
        }
    }

    /** O nível voltou a NC3+ no mesmo dia: a lista volta a valer com o andamento dos contatos. */
    public void reabrir() {
        status = StatusLista.ABERTA;
        encerradaEm = null;
    }

    /**
     * Guarda o maior nível do dia, que define o alcance. Se o nível cair (ex.: NC5 para NC4), quem já
     * está na lista continua nela, para não perder contatos em andamento.
     */
    public void elevarNivel(int nivel) {
        if (nivel > nivelCalor) {
            nivelCalor = nivel;
        }
    }

    public boolean isAberta() {
        return status == StatusLista.ABERTA;
    }
}
