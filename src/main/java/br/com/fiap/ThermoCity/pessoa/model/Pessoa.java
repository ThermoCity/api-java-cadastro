package br.com.fiap.ThermoCity.pessoa.model;

import br.com.fiap.ThermoCity.territorio.model.Servico;
import br.com.fiap.ThermoCity.territorio.model.Setor;
import br.com.fiap.ThermoCity.territorio.service.Localizacao;
import br.com.fiap.ThermoCity.user.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;

/**
 * Pessoa acompanhada (60+). A mesma tabela guarda as 5.000 pessoas sintéticas geradas do Censo
 * (sintetico = true, sem titular) e os cadastros reais feitos pelos cidadãos (sintetico = false).
 */
@Entity
@Table(name = "tmc_pessoa_sintetica")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Pessoa {

    public static final int IDADE_MINIMA = 60;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_pessoa")
    // A sequence do banco tem increment 50; o allocationSize precisa ser igual
    @SequenceGenerator(name = "seq_pessoa", sequenceName = "tmc_seq_pessoa_sintetica", allocationSize = 50)
    private Long id;

    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    /** Idade gravada; para cadastros reais é recalculada pela data de nascimento. */
    @Column(name = "idade")
    private Integer idade;

    @Column(name = "faixa_etaria", length = 10)
    private String faixaEtaria;

    @Column(name = "sexo", length = 1)
    private String sexo;

    @Column(name = "data_nascimento")
    private LocalDate dataNascimento;

    @Column(name = "telefone", length = 30)
    private String telefone;

    @Column(name = "endereco", length = 200)
    private String endereco;

    @Column(name = "cep", length = 8)
    private String cep;

    @Column(name = "lat")
    private Double latitude;

    @Column(name = "lon")
    private Double longitude;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "setor_codigo", nullable = false)
    private Setor setor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ubs_ref_id")
    private Servico ubs;

    @Column(name = "ubs_ref_nome", length = 300)
    private String ubsNome;

    @Column(name = "mora_sozinho", nullable = false)
    private boolean moraSozinho;

    @Column(name = "mobilidade_reduzida", nullable = false)
    private boolean mobilidadeReduzida;

    @Column(name = "condicao_cronica", nullable = false)
    private boolean condicaoCronica;

    @Column(name = "contato_ref_nome", length = 120)
    private String contatoReferenciaNome;

    @Column(name = "contato_ref_telefone", length = 30)
    private String contatoReferenciaTelefone;

    @Column(name = "consentimento_lgpd", nullable = false)
    private boolean consentimentoLgpd;

    @Column(name = "consentimento_em")
    private OffsetDateTime consentimentoEm;

    /** Titular do cadastro (o cidadão que cadastrou). Nulo nas pessoas sintéticas. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "titular_id")
    private User titular;

    @Column(name = "sintetico", nullable = false)
    private boolean sintetico;

    @Column(name = "semente")
    private Long semente;

    @Column(name = "gerado_em")
    private OffsetDateTime geradoEm;

    @Column(name = "atualizado_em")
    private OffsetDateTime atualizadoEm;

    /** Novo cadastro real feito por um cidadão, já com consentimento. */
    public static Pessoa cadastrar(User titular, DadosPessoa dados, LocalDate hoje, Localizacao localizacao,
                                   OffsetDateTime agora) {
        Pessoa pessoa = new Pessoa();
        pessoa.titular = titular;
        pessoa.sintetico = false;
        pessoa.consentimentoLgpd = true;
        pessoa.consentimentoEm = agora;
        pessoa.geradoEm = agora;
        pessoa.aplicar(dados, hoje, localizacao, agora);
        return pessoa;
    }

    /** Atualização feita pelo titular; setor e UBS só mudam se a localização mudou. */
    public void atualizar(DadosPessoa dados, LocalDate hoje, Localizacao novaLocalizacao, OffsetDateTime agora) {
        aplicar(dados, hoje, novaLocalizacao, agora);
    }

    public boolean coordenadaDiferente(double lat, double lon) {
        return latitude == null || longitude == null
                || Double.compare(latitude, lat) != 0 || Double.compare(longitude, lon) != 0;
    }

    public boolean isTitular(User usuario) {
        return titular != null && usuario != null && titular.getIdUsuario().equals(usuario.getIdUsuario());
    }

    /** Idade atual: pela data de nascimento quando existe; senão a idade gravada (sintéticos). */
    public Integer idadeEm(LocalDate hoje) {
        return dataNascimento != null ? Period.between(dataNascimento, hoje).getYears() : idade;
    }

    public String getUbsId() {
        return ubs != null ? ubs.getId() : null;
    }

    private void aplicar(DadosPessoa dados, LocalDate hoje, Localizacao localizacao, OffsetDateTime agora) {
        this.nome = dados.nome();
        this.dataNascimento = dados.dataNascimento();
        this.idade = Period.between(dados.dataNascimento(), hoje).getYears();
        this.faixaEtaria = idade >= 70 ? "70+" : "60-69";
        this.sexo = dados.sexo();
        this.telefone = dados.telefone();
        this.endereco = dados.endereco();
        this.cep = dados.cep();
        this.latitude = dados.latitude();
        this.longitude = dados.longitude();
        this.moraSozinho = dados.moraSozinho();
        this.mobilidadeReduzida = dados.mobilidadeReduzida();
        this.condicaoCronica = dados.condicaoCronica();
        this.contatoReferenciaNome = dados.contatoReferenciaNome();
        this.contatoReferenciaTelefone = dados.contatoReferenciaTelefone();
        if (localizacao != null) {
            this.setor = localizacao.setor();
            this.ubs = localizacao.ubs();
            this.ubsNome = localizacao.ubs().getNome();
        }
        this.atualizadoEm = agora;
    }

    /** Dados já normalizados que o cidadão informa no cadastro e na alteração. */
    public record DadosPessoa(String nome, LocalDate dataNascimento, String sexo, String telefone, String endereco,
                              String cep, double latitude, double longitude, boolean moraSozinho,
                              boolean mobilidadeReduzida, boolean condicaoCronica, String contatoReferenciaNome,
                              String contatoReferenciaTelefone) {
    }
}
