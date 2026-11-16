package br.com.fiap.ThermoCity.territorio.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/** UBS ou refúgio climático (camada ONDE). Somente leitura. */
@Entity
@Immutable
@Table(name = "tmc_servico")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Servico {

    public static final String TIPO_UBS = "UBS";
    public static final String TIPO_REFUGIO = "REFUGIO";

    @Id
    @Column(name = "id", length = 40)
    private String id;

    @Column(name = "nome", length = 300)
    private String nome;

    @Column(name = "tipo", length = 20)
    private String tipo;

    @Column(name = "categoria")
    private String categoria;

    @Column(name = "endereco")
    private String endereco;

    @Column(name = "bairro")
    private String bairro;

    @Column(name = "lat")
    private Double lat;

    @Column(name = "lon")
    private Double lon;

    @Column(name = "revisao")
    private String revisao;

    public Servico(String id, String nome, String tipo, Double lat, Double lon, String revisao) {
        this.id = id;
        this.nome = nome;
        this.tipo = tipo;
        this.lat = lat;
        this.lon = lon;
        this.revisao = revisao;
    }
}
