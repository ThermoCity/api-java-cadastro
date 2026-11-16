package br.com.fiap.ThermoCity.pessoa.repository;

import br.com.fiap.ThermoCity.pessoa.model.OrientacaoContato;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface OrientacaoContatoRepository extends JpaRepository<OrientacaoContato, Long> {

    @Modifying
    @Query("delete from OrientacaoContato o where o.pessoaId = :pessoaId")
    int apagarDaPessoa(Long pessoaId);
}
