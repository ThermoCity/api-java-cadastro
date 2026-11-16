package br.com.fiap.ThermoCity.busca.model;

import br.com.fiap.ThermoCity.pessoa.model.Pessoa;
import br.com.fiap.ThermoCity.territorio.model.Setor;
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

/** Pessoa dentro de uma lista do dia, com posição, pontuação e motivos da prioridade. */
@Entity
@Table(name = "tmc_lista_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ListaItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lista_id", nullable = false)
    private ListaBuscaAtiva lista;

    /** Fica nulo quando a pessoa exerce o direito de eliminação (LGPD). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pessoa_id")
    private Pessoa pessoa;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "setor_codigo", nullable = false)
    private Setor setor;

    @Column(name = "posicao", nullable = false)
    private Integer posicao;

    @Column(name = "pontuacao", nullable = false)
    private Integer pontuacao;

    @Column(name = "motivos", nullable = false, length = 500)
    private String motivos;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_contato", nullable = false, length = 20)
    private StatusContato statusContato;

    @Column(name = "ultimo_contato_em")
    private OffsetDateTime ultimoContatoEm;

    public ListaItem(ListaBuscaAtiva lista, Pessoa pessoa, Setor setor, int posicao, int pontuacao, String motivos) {
        this.lista = lista;
        this.pessoa = pessoa;
        this.setor = setor;
        this.posicao = posicao;
        this.pontuacao = pontuacao;
        this.motivos = motivos;
        this.statusContato = StatusContato.PENDENTE;
    }

    /** O último contato registrado define a situação da pessoa na lista. */
    public void registrarContato(StatusContato status, OffsetDateTime quando) {
        this.statusContato = status;
        this.ultimoContatoEm = quando;
    }
}
