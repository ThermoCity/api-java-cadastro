package br.com.fiap.ThermoCity.contato.model;

import br.com.fiap.ThermoCity.busca.model.ListaItem;
import br.com.fiap.ThermoCity.pessoa.model.Pessoa;
import br.com.fiap.ThermoCity.territorio.model.Servico;
import br.com.fiap.ThermoCity.user.model.User;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/** Registro de uma tentativa de contato da busca ativa. */
@Entity
@Table(name = "tmc_contato")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Contato {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Fica nulo quando a pessoa é excluída (histórico anonimizado, LGPD). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pessoa_id")
    private Pessoa pessoa;

    /** Item da lista do dia atualizado por este contato; nulo se a pessoa não estava em lista aberta. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lista_item_id")
    private ListaItem listaItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agente_id", nullable = false)
    private User agente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ubs_id", nullable = false)
    private Servico ubs;

    @Enumerated(EnumType.STRING)
    @Column(name = "resultado", nullable = false, length = 20)
    private ResultadoContato resultado;

    @Column(name = "observacao", length = 1000)
    private String observacao;

    @Column(name = "realizado_em", nullable = false)
    private OffsetDateTime realizadoEm;

    public Contato(Pessoa pessoa, ListaItem listaItem, User agente, Servico ubs, ResultadoContato resultado,
                   String observacao, OffsetDateTime realizadoEm) {
        this.pessoa = pessoa;
        this.listaItem = listaItem;
        this.agente = agente;
        this.ubs = ubs;
        this.resultado = resultado;
        this.observacao = observacao;
        this.realizadoEm = realizadoEm;
    }
}
