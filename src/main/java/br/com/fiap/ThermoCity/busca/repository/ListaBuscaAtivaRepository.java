package br.com.fiap.ThermoCity.busca.repository;

import br.com.fiap.ThermoCity.busca.model.ListaBuscaAtiva;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Optional;

public interface ListaBuscaAtivaRepository extends JpaRepository<ListaBuscaAtiva, Long> {

    boolean existsByDataLista(LocalDate dataLista);

    @Query("select max(l.nivelCalor) from ListaBuscaAtiva l where l.dataLista = :data")
    Optional<Integer> nivelDoDia(LocalDate data);
}
