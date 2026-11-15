package br.com.fiap.ThermoCity.pessoa.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Roteiro de contato gerado por pessoa e dia (preenchido pela feature de Spring AI).
 * Mapeado aqui só com as colunas necessárias para apagar os registros na exclusão LGPD,
 * já que a tabela não tem chave estrangeira para a pessoa.
 */
@Entity
@Table(name = "tmc_orientacao_contato")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrientacaoContato {

    @Id
    private Long id;

    @Column(name = "pessoa_id", nullable = false)
    private Long pessoaId;

    @Column(name = "data_lista", nullable = false)
    private LocalDate dataLista;
}
