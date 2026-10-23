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

	List<ChaveApi> findByUsuarioIdOrderByCriadaEmDescIdDesc(Long usuarioId);

	Optional<ChaveApi> findByIdAndUsuarioId(Long id, Long usuarioId);

	// UPDATE só da coluna de último uso, em vez de save() da entidade inteira: o save()
	// regravaria todas as colunas com o que foi lido no início da requisição, então uma
	// revogação feita nesse meio-tempo voltaria a revogada_em = NULL (D5 quebrado).
	@Modifying
	@Transactional
	@Query("update ChaveApi c set c.ultimoUsoEm = :agora where c.id = :id")
	void atualizarUltimoUso(@Param("id") Long id, @Param("agora") Instant agora);

	// Incremento 81: dono e Admin podem revogar a mesma chave ao mesmo tempo. A condição
	// "ainda não revogada" no próprio UPDATE garante que só um vence (o outro recebe 409) e
	// que quem revogou não é sobrescrito (D5); também não regrava ultimo_uso_em, como o
	// save() da entidade inteira faria. @return linhas alteradas (0 = já estava revogada).
	// flush/clear: o UPDATE vai direto ao banco; sem limpar o contexto, uma leitura seguinte
	// na mesma transação devolveria a entidade em cache, ainda "ativa".
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Transactional
	@Query("update ChaveApi c set c.revogadaEm = :agora, c.revogadaPorUsuarioId = :quem where c.id = :id and c.revogadaEm is null")
	int revogarSeAtiva(@Param("id") Long id, @Param("quem") Long quem, @Param("agora") Instant agora);

}
