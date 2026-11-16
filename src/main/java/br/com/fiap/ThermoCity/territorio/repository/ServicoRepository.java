package br.com.fiap.ThermoCity.territorio.repository;

import br.com.fiap.ThermoCity.territorio.model.Servico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServicoRepository extends JpaRepository<Servico, String> {

    List<Servico> findByTipo(String tipo);
}
