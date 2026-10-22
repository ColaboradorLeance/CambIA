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

	boolean existsByCalculoId(Long calculoId);

	@Query("select b.nome from Banco b where b.id = :id")
	Optional<String> findNomeById(@Param("id") Long id);

	// left join: um banco sem modelo de cálculo (fórmula null) ainda precisa devolver
	// nome e taxa de rebate — mesmo contrato dos finders individuais que isto substitui
	// nas listagens de Operações.
	@Query("select new com.cambia.banco.BancoResumo(b.nome, c.formula, b.taxaRebate) from Banco b left join b.calculo c where b.id = :id")
	Optional<BancoResumo> findResumoById(@Param("id") Long id);

}
