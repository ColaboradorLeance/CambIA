package com.cambia.cliente;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

	// Incremento 80: filtros de texto da listagem paginada de ordens resolvem o nome em ids
	// numa tabela pequena e a consulta principal usa "IN (ids)" no índice de operacoes.
	// padrao já vem em minúsculas, com % e _ escapados (\) e envolto em % pelo chamador.
	@Query("select c.id from Cliente c where lower(c.nome) like :padrao escape '\\'")
	java.util.List<Long> findIdsPorNome(@Param("padrao") String padrao);

	@Query("select c.id from Cliente c where lower(c.documento) like :padrao escape '\\'")
	java.util.List<Long> findIdsPorDocumento(@Param("padrao") String padrao);

	@Query("select c.nome from Cliente c where c.id = :id")
	Optional<String> findNomeById(@Param("id") Long id);

	@Query("select new com.cambia.cliente.ClienteResumo(c.nome, c.documento) from Cliente c where c.id = :id")
	Optional<ClienteResumo> findResumoById(@Param("id") Long id);

}
