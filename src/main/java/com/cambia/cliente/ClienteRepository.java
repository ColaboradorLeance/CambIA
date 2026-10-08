package com.cambia.cliente;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

	@Query("select c.nome from Cliente c where c.id = :id")
	Optional<String> findNomeById(@Param("id") Long id);

	@Query("select new com.cambia.cliente.ClienteResumo(c.nome, c.documento) from Cliente c where c.id = :id")
	Optional<ClienteResumo> findResumoById(@Param("id") Long id);

}
