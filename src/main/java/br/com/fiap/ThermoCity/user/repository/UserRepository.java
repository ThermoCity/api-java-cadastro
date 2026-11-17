package br.com.fiap.ThermoCity.user.repository;

import br.com.fiap.ThermoCity.user.model.User;
import br.com.fiap.ThermoCity.user.model.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByCpf(String cpf);

    /** Filtros opcionais: null ignora o filtro. */
    @Query("""
            select u from User u
            where (:papel is null or u.papel = :papel)
              and (:ubsId is null or u.ubsId = :ubsId)""")
    Page<User> buscar(UserRole papel, String ubsId, Pageable pageable);
}
