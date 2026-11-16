package br.com.fiap.ThermoCity.busca.repository;

import br.com.fiap.ThermoCity.busca.model.EventoProcessado;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoProcessadoRepository extends JpaRepository<EventoProcessado, String> {
}
