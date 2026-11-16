package br.com.fiap.ThermoCity.pessoa.repository;

import br.com.fiap.ThermoCity.pessoa.model.Pessoa;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PessoaRepository extends JpaRepository<Pessoa, Long> {

    /** Busca com filtros opcionais (null = sem filtro). O nome é comparado sem diferenciar maiúsculas. */
    @EntityGraph(attributePaths = {"setor", "ubs", "titular"})
    @Query(value = """
            select p from Pessoa p
             where (:titularId is null or p.titular.idUsuario = :titularId)
               and (:ubsId is null or p.ubs.id = :ubsId)
               and (:setorCodigo is null or p.setor.codigo = :setorCodigo)
               and (:nome is null or lower(p.nome) like :nome)""",
            countQuery = """
            select count(p) from Pessoa p
             where (:titularId is null or p.titular.idUsuario = :titularId)
               and (:ubsId is null or p.ubs.id = :ubsId)
               and (:setorCodigo is null or p.setor.codigo = :setorCodigo)
               and (:nome is null or lower(p.nome) like :nome)""")
    Page<Pessoa> buscar(Long titularId, String ubsId, String setorCodigo, String nome, Pageable pageable);

    @EntityGraph(attributePaths = {"setor", "ubs", "titular"})
    Optional<Pessoa> findComRelacionamentosById(Long id);

    /**
     * Todas as pessoas acompanhadas, com setor e UBS, para gerar a lista do dia. Usa left join para que
     * as pessoas sem setor ou UBS também venham e sejam registradas para correção do cadastro.
     */
    @Query("select p from Pessoa p left join fetch p.setor left join fetch p.ubs")
    List<Pessoa> findParaBuscaAtiva();
}
