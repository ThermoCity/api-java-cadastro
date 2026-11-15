package br.com.fiap.ThermoCity.busca.repository;

import br.com.fiap.ThermoCity.busca.model.ListaItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
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

    @Query("""
            select new br.com.fiap.ThermoCity.busca.repository.CoberturaLinha(
                   u.id, u.nome, s.codigo, s.bairro, i.statusContato, count(i))
              from ListaItem i join i.lista l join l.ubs u join i.setor s
             where l.dataLista = :data
             group by u.id, u.nome, s.codigo, s.bairro, i.statusContato""")
    List<CoberturaLinha> contarPorUbsSetorStatus(LocalDate data);
}
