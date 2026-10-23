package com.cambia.banco;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BancoRepository extends JpaRepository<Banco, Long> {

	// Incremento 80: filtros de texto da listagem paginada de ordens resolvem o nome em ids
	// numa tabela pequena e a consulta principal usa "IN (ids)" no índice de operacoes.
	// padrao já vem em minúsculas, com % e _ escapados (\) e envolto em % pelo chamador.
	@Query("select b.id from Banco b where lower(b.nome) like :padrao escape '\\'")
	java.util.List<Long> findIdsPorNome(@Param("padrao") String padrao);

	boolean existsByCalculosId(Long calculoId);

	/** Ids dos modelos de cálculo do banco (Incremento 84). */
	@Query("select c.id from Banco b join b.calculos c where b.id = :id")
	java.util.List<Long> findCalculoIdsById(@Param("id") Long id);

	/** Só os ativos (Incremento 85) — o que uma ordem nova pode escolher. */
	@Query("select c.id from Banco b join b.calculos c where b.id = :id and c.ativo = true")
	java.util.List<Long> findCalculoAtivoIdsById(@Param("id") Long id);

	@Query("select b.nome from Banco b where b.id = :id")
	Optional<String> findNomeById(@Param("id") Long id);

	// Incremento 84: a fórmula saiu daqui — agora é a do cálculo que a ORDEM escolheu
	// (CalculoRepository.findResumoById), não a do banco.
	@Query("select new com.cambia.banco.BancoResumo(b.nome, b.taxaRebate) from Banco b where b.id = :id")
	Optional<BancoResumo> findResumoById(@Param("id") Long id);

}
