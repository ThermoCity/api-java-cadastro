package br.com.fiap.ThermoCity.contato.repository;

import br.com.fiap.ThermoCity.contato.model.Contato;
import br.com.fiap.ThermoCity.contato.model.ResultadoContato;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ContatoRepository extends JpaRepository<Contato, Long> {

    /** Histórico com filtros opcionais (null = sem filtro). */
    @EntityGraph(attributePaths = {"pessoa", "agente", "ubs"})
    @Query(value = """
            select c from Contato c
             where (:pessoaId is null or c.pessoa.id = :pessoaId)
               and (:ubsId is null or c.ubs.id = :ubsId)
               and (:resultado is null or c.resultado = :resultado)""",
            countQuery = """
            select count(c) from Contato c
             where (:pessoaId is null or c.pessoa.id = :pessoaId)
               and (:ubsId is null or c.ubs.id = :ubsId)
               and (:resultado is null or c.resultado = :resultado)""")
    Page<Contato> buscar(Long pessoaId, String ubsId, ResultadoContato resultado, Pageable pageable);

    @EntityGraph(attributePaths = {"pessoa", "agente", "ubs"})
    Optional<Contato> findComRelacionamentosById(Long id);

    /** Exclusão LGPD: mantém o contato (data, agente, resultado) sem a pessoa e sem a observação livre. */
    @Modifying
    @Query("update Contato c set c.pessoa = null, c.observacao = null where c.pessoa.id = :pessoaId")
    int anonimizarPorPessoa(Long pessoaId);
}
