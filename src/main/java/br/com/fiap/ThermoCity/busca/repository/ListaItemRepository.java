package br.com.fiap.ThermoCity.busca.repository;

import br.com.fiap.ThermoCity.busca.model.ListaItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ListaItemRepository extends JpaRepository<ListaItem, Long> {

    /** Item da pessoa na lista ABERTA do dia (cada pessoa pertence a uma única UBS, logo a uma única lista). */
    @Query("""
            select i from ListaItem i join fetch i.lista l
             where i.pessoa.id = :pessoaId
               and l.dataLista = :data
               and l.status = br.com.fiap.ThermoCity.busca.model.StatusLista.ABERTA""")
    Optional<ListaItem> findItemAberto(Long pessoaId, LocalDate data);

    /** Exclusão LGPD: o item continua contando na cobertura, mas sem vínculo com a pessoa. */
    @Modifying
    @Query("update ListaItem i set i.pessoa = null where i.pessoa.id = :pessoaId")
    int desvincularPessoa(Long pessoaId);

    /** Itens das listas informadas, por UBS e posição (a pessoa pode ser nula após exclusão LGPD). */
    @Query(value = """
            select i from ListaItem i
              join fetch i.lista l join fetch l.ubs u join fetch i.setor s left join fetch i.pessoa p
             where l.id in :listaIds
             order by u.nome, u.id, i.posicao""",
            countQuery = "select count(i) from ListaItem i where i.lista.id in :listaIds")
    Page<ListaItem> buscarDasListas(Collection<Long> listaIds, Pageable pageable);

    @Query("""
            select new br.com.fiap.ThermoCity.busca.repository.CoberturaLinha(
                   u.id, u.nome, s.codigo, s.bairro, i.statusContato, count(i))
              from ListaItem i join i.lista l join l.ubs u join i.setor s
             where l.dataLista = :data
             group by u.id, u.nome, s.codigo, s.bairro, i.statusContato""")
    List<CoberturaLinha> contarPorUbsSetorStatus(LocalDate data);
}
