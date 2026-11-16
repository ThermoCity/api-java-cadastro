package br.com.fiap.ThermoCity.territorio.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * Nota de calor de cada setor (somente leitura): quintil da temperatura de superfície no verão,
 * medida por imagem Landsat. 1 = setor mais fresco, 5 = setor mais quente.
 */
@Entity
@Immutable
@Table(name = "tmc_calor_setor")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CalorSetor {

    @Id
    @Column(name = "codigo_setor", length = 15)
    private String codigoSetor;

    @Column(name = "classe_calor")
    private Integer classeCalor;

    @Column(name = "lst_verao_c")
    private Double temperaturaSuperficieC;

    @Column(name = "status", length = 20)
    private String status;
}
