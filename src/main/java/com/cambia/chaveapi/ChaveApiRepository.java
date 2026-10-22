package com.cambia.chaveapi;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

interface ChaveApiRepository extends JpaRepository<ChaveApi, Long> {

	Optional<ChaveApi> findByHash(String hash);

	List<ChaveApi> findAllByOrderByCriadaEmDescIdDesc();

	// UPDATE só da coluna de último uso, em vez de save() da entidade inteira: o save()
	// regravaria todas as colunas com o que foi lido no início da requisição, então uma
	// revogação feita nesse meio-tempo voltaria a revogada_em = NULL (D5 quebrado).
	@Modifying
	@Transactional
	@Query("update ChaveApi c set c.ultimoUsoEm = :agora where c.id = :id")
	void atualizarUltimoUso(@Param("id") Long id, @Param("agora") Instant agora);

}
