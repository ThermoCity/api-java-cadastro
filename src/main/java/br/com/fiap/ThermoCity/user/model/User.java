package br.com.fiap.ThermoCity.user.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "tmc_usuario")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_USUARIO")
    private Long idUsuario;

    @Column(name = "NOME", nullable = false, length = 150)
    private String nome;

    @Column(name = "EMAIL", nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "SENHA", nullable = false)
    private String senha;

    @Column(name = "TELEFONE", nullable = false, length = 11)
    private String telefone;

    @Column(name = "CPF", nullable = false, unique = true, length = 11)
    private String cpf;

    @Column(name = "CEP", nullable = false, length = 8)
    private String cep;

    @Column(name = "LATITUDE")
    private Double latitude;

    @Column(name = "LONGITUDE")
    private Double longitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "PAPEL", nullable = false, length = 20)
    private UserRole papel;

    @Column(name = "DATA_CRIACAO", nullable = false)
    private OffsetDateTime dataCriacao;

    /** UBS de atuação; obrigatória para AGENTE (id de tmc_servico). */
    @Column(name = "UBS_ID", length = 40)
    private String ubsId;

    public User(String nome, String email, String senhaHash, String telefone, String cpf, String cep,
                Double latitude, Double longitude, UserRole papel) {
        this(nome, email, senhaHash, telefone, cpf, cep, latitude, longitude, papel, null);
    }

    public User(String nome, String email, String senhaHash, String telefone, String cpf, String cep,
                Double latitude, Double longitude, UserRole papel, String ubsId) {
        this.ubsId = ubsId;
        this.nome = nome;
        this.email = email;
        this.senha = senhaHash;
        this.telefone = telefone;
        this.cpf = cpf;
        this.cep = cep;
        this.latitude = latitude;
        this.longitude = longitude;
        this.papel = papel;
        this.dataCriacao = OffsetDateTime.now();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + papel.name()));
    }

    @Override
    public String getPassword() {
        return senha;
    }

    @Override
    public String getUsername() {
        return email;
    }
}
