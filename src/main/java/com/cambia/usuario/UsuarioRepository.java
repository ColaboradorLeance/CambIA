package com.cambia.usuario;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

	// Incremento 80: filtros de texto da listagem paginada de ordens resolvem o nome em ids
	// numa tabela pequena e a consulta principal usa "IN (ids)" no índice de operacoes.
	// padrao já vem em minúsculas, com % e _ escapados (\) e envolto em % pelo chamador.
	@org.springframework.data.jpa.repository.Query("select u.id from Usuario u where lower(u.nome) like :padrao escape '\\'")
	java.util.List<Long> findIdsPorNome(@org.springframework.data.repository.query.Param("padrao") String padrao);

	Optional<Usuario> findByEmail(String email);

	boolean existsByEmail(String email);

}
