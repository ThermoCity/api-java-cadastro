package br.com.fiap.ThermoCity.busca.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Evento de nível de calor já tratado: garante que um evento repetido não gere outra lista. */
@Entity
@Table(name = "tmc_evento_processado")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventoProcessado {

    @Id
    @Column(name = "evento_id", length = 64)
    private String eventoId;

    @Column(name = "data_local", nullable = false)
    private LocalDate dataLocal;

    @Column(name = "nivel_novo", nullable = false)
    private Integer nivelNovo;

    @Column(name = "acao", nullable = false, length = 20)
    private String acao;

    @Column(name = "listas_afetadas", nullable = false)
    private Integer listasAfetadas;

    @Column(name = "processado_em", nullable = false)
    private OffsetDateTime processadoEm;

    public EventoProcessado(String eventoId, LocalDate dataLocal, int nivelNovo, AcaoEvento acao,
                            int listasAfetadas, OffsetDateTime processadoEm) {
        this.eventoId = eventoId;
        this.dataLocal = dataLocal;
        this.nivelNovo = nivelNovo;
        this.acao = acao.name();
        this.listasAfetadas = listasAfetadas;
        this.processadoEm = processadoEm;
    }
}
