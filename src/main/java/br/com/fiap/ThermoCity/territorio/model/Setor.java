package br.com.fiap.ThermoCity.territorio.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/** Setor censitário (camada ONDE). Somente leitura: a tabela é mantida pela base territorial. */
@Entity
@Immutable
@Table(name = "tmc_setor")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Setor {

    @Id
    @Column(name = "codigo", length = 15)
    private String codigo;

    @Column(name = "nm_bairro")
    private String bairro;

    @Column(name = "ipvs_grupo")
    private Integer ipvsGrupo;

    @Column(name = "lat_referencia")
    private Double latReferencia;

    @Column(name = "lon_referencia")
    private Double lonReferencia;

    /** POLYGON em WKT, com coordenadas na ordem (longitude latitude). */
    @Column(name = "geometria_wkt", length = 200_000)
    private String geometriaWkt;

    public Setor(String codigo, String bairro, Integer ipvsGrupo, Double latReferencia, Double lonReferencia,
                 String geometriaWkt) {
        this.codigo = codigo;
        this.bairro = bairro;
        this.ipvsGrupo = ipvsGrupo;
        this.latReferencia = latReferencia;
        this.lonReferencia = lonReferencia;
        this.geometriaWkt = geometriaWkt;
    }
}
